package kr.chagok.data

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kr.chagok.data.db.ActiveTripEntity
import kr.chagok.data.db.AdminEditEntity
import kr.chagok.data.db.ChagokDatabase
import kr.chagok.data.db.TripEntity
import kr.chagok.domain.model.ActiveTrip
import kr.chagok.domain.model.AdminEdit
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.domain.model.Trip
import kr.chagok.domain.model.TripPlace
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 운행 기록 저장소와 분류 바꾸기.
 * 개인으로 바꾸면 위치를 지우되, 잘못 누른 1탭에 장소가 사라지지 않게 확정([commitPlaceDeletion]) 전까지는 지운 장소를
 * 메모리에만 들고 있다가 다시 업무로 바꾸면 살린다. 관리자 PIN 으로 고친 것은 근거가 "관리자 수정"이 되고
 * [AdminEdit] 로 바꾸기 전·후가 남는다.
 */
class TripRepository(
    private val db: ChagokDatabase,
    private val clock: Clock,
    scope: CoroutineScope,
) {
    private val tripDao = db.tripDao()
    private val activeTripDao = db.activeTripDao()
    private val adminEditDao = db.adminEditDao()
    private val editLock = Mutex()

    val trips: StateFlow<List<Trip>> = tripDao.observeAll()
        .map { list -> list.map(TripEntity::toDomain) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** 관리자 수정 기록 (최근 것 먼저) */
    val adminEdits: Flow<List<AdminEdit>> =
        adminEditDao.observeAll().map { list -> list.map(AdminEditEntity::toDomain) }

    private val placeUndo = MutableStateFlow<PlaceUndo?>(null)

    /** 개인으로 바꾸며 지운 장소를 아직 되살릴 수 있는 운행 */
    val restorableTripId: StateFlow<Long?> = placeUndo
        .map { it?.tripId }
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun find(id: Long): Trip? = tripDao.find(id)?.toDomain()

    /** 저장 순서대로 전부. 화면 목록([trips])이 아직 안 읽혔을 때도 디스크에서 바로 읽는다 (지난 방문 안내) */
    suspend fun all(): List<Trip> = tripDao.all().map(TripEntity::toDomain)

    /** 마지막 운행의 도착 계기판 값. 기록이 없으면 null (빠진 거리를 따질 기준이 없다) */
    suspend fun lastEndKm(): Double? = tripDao.lastEndKm()

    /** 그날 마지막으로 끝난 운행. 화면 목록([trips])이 아직 안 읽혔어도 디스크에서 바로 찾는다 (Gleo 음성) */
    suspend fun lastTripOn(date: LocalDate): Trip? = tripDao.lastTripOn(date)?.toDomain()

    suspend fun activeTrip(): ActiveTrip? = activeTripDao.find()?.toDomain()

    /** 출발 때 열고, 주행 중 내비 목적지를 받으면 다시 쓴다 */
    suspend fun saveActiveTrip(trip: ActiveTrip) = write("진행 중 운행 저장") {
        activeTripDao.save(ActiveTripEntity.from(trip))
    }

    suspend fun checkpoint(km: Double, at: LocalTime) = write("계기판 저장") {
        activeTripDao.checkpoint(km, at)
    }

    /** 달리던 운행을 기록부로 옮긴다. 운행 저장과 진행 중 표 비우기를 한 번에 */
    suspend fun closeActiveTrip(trip: Trip): Long = write("주차 저장") {
        db.withTransaction {
            val id = tripDao.insert(TripEntity.from(trip))
            activeTripDao.clear()
            id
        }
    }

    /**
     * 다른 곳에 있던 기록을 한 번에 넣는다 (디버그 빌드의 샘플, 나중에 백업 복원). 기존 기록은 지우지 않는다.
     * 이미 있는 운행(같은 uid)은 건너뛴다. 같은 백업을 두 번 되살려도 기록부 줄이 겹치지 않는다
     */
    suspend fun importTrips(list: List<Trip>) = write("기록 가져오기") {
        db.withTransaction { list.forEach { tripDao.insertIfNew(TripEntity.from(it.copy(id = 0))) } }
    }

    /** 앱이 놓친 구간. 시각·장소·사람을 모르고, 분류는 사용자가 고른다 */
    suspend fun addGap(date: LocalDate, startKm: Double, endKm: Double): Long = write("빠진 주행 저장") {
        tripDao.insert(
            TripEntity.from(
                Trip(
                    date = date, personId = null, origin = null, destination = null,
                    startKm = startKm, endKm = endKm, startedAt = null, endedAt = null,
                    category = null, reason = null, isGap = true,
                ),
            ),
        )
    }

    /**
     * 사용자가 분류를 바꾸거나(사용자 수정) 기록 안 된 주행을 고른다(사용자 선택).
     * 방금 개인으로 바꾼 운행을 다시 업무 쪽으로 바꾸면 지웠던 장소가 돌아온다.
     */
    suspend fun classify(id: Long, category: Category, isAdminEdit: Boolean = false) = editLock.withLock {
        val trip = find(id) ?: return@withLock
        if (trip.category == category) return@withLock
        val undo = placeUndo.value?.takeIf { it.tripId == id && category != Category.PERSONAL }
        val changed = if (undo != null) restorePlaces(trip, undo, category) else changeCategory(trip, category)
        saveClassified(trip, changed, isAdminEdit)
        // 되살리기 상태는 저장된 뒤에만 바꾼다. 저장이 실패하면 운행도 되살리기도 그대로라 다시 할 수 있다
        when {
            undo != null -> placeUndo.value = null
            !trip.isGap && category == Category.PERSONAL ->
                placeUndo.value = PlaceUndo(trip.id, trip.category, trip.reason, trip.origin, trip.destination)
        }
    }

    /**
     * 방금 운행 카드의 "이건 업무였어요". 방금 개인으로 바꾼 거면 원래 분류·장소·근거로 되돌리고,
     * 처음부터 개인으로 기록됐으면 일반 업무로 바꾼다.
     */
    suspend fun revertToWork(id: Long, isAdminEdit: Boolean = false) {
        val isRestored = editLock.withLock {
            val undo = placeUndo.value?.takeIf { it.tripId == id } ?: return@withLock false
            val trip = find(id) ?: return@withLock true
            saveClassified(trip, restorePlaces(trip, undo, undo.category ?: Category.BUSINESS), isAdminEdit)
            placeUndo.value = null
            true
        }
        if (!isRestored) classify(id, Category.BUSINESS, isAdminEdit)
    }

    /**
     * 주차 때 도착지 없이 먼저 저장한 운행([saved])에, 나중에 받은 그 자리 주소로 고친 값([arrived])을 쓴다 (기록기만).
     * 그사이 사용자가 분류를 고쳤으면 분류는 그대로 두고 도착지만, 개인으로 바꿨으면 위치를 남기지 않는다.
     * 저장된 모습을 돌려준다. 운행이 없어졌으면 null
     */
    suspend fun fillArrival(saved: Trip, arrived: Trip): Trip? = editLock.withLock {
        val trip = find(saved.id) ?: return@withLock null
        if (trip.isPersonal) return@withLock trip
        val isUntouched = trip.category == saved.category && trip.reason == saved.reason
        val filled = if (isUntouched) {
            trip.copy(destination = arrived.destination, category = arrived.category, reason = arrived.reason)
        } else {
            trip.copy(destination = arrived.destination)
        }
        save(filled)
        filled
    }

    /**
     * 방금 운행 카드·운행 상세를 닫거나 다음에 출발하면 장소 삭제를 확정한다.
     * [tripId] 를 주면 그 운행의 되살리기만 확정한다 (다른 운행 시트를 열었다 닫았다고 카드의 되살리기가 사라지지 않게)
     */
    fun commitPlaceDeletion(tripId: Long? = null) {
        placeUndo.update { undo -> if (tripId == null || undo?.tripId == tripId) null else undo }
    }

    /** 미지정 운행에 사람을 고르거나(누구나), 이미 정해진 사람을 바꾼다(관리자만 — 화면이 막는다) */
    suspend fun assignPerson(id: Long, personId: Long, isAdminEdit: Boolean = false) = editLock.withLock {
        val trip = find(id) ?: return@withLock
        if (trip.personId == personId) return@withLock
        // 사람만 바꾼 것은 수정 기록에만 남는다 (분류 근거는 분류 이야기다)
        val edit = if (isAdminEdit) {
            adminEdit(trip, AdminEdit.Field.PERSON, trip.personId?.toString(), personId.toString())
        } else {
            null
        }
        save(trip.copy(personId = personId), edit)
    }

    suspend fun setNote(id: Long, note: String) = editLock.withLock {
        val trip = find(id) ?: return@withLock
        save(trip.copy(note = note))
    }

    private fun changeCategory(trip: Trip, category: Category): Trip {
        val reason = if (trip.isGap) ClassifyReason.USER_PICK else ClassifyReason.USER_EDIT
        return trip.reclassified(category, reason)
    }

    // 지웠던 장소를 살린다. 원래 분류로 돌아가면 근거도 원래대로, 다른 분류면 사용자 수정
    private fun restorePlaces(trip: Trip, undo: PlaceUndo, category: Category): Trip {
        val reason = if (category == undo.category) undo.reason else ClassifyReason.USER_EDIT
        return trip.copy(category = category, reason = reason, origin = undo.origin, destination = undo.destination)
    }

    // 관리자 수정이면 근거를 "관리자 수정"으로 바꾸고 바꾸기 전·후 분류를 남긴다
    private suspend fun saveClassified(before: Trip, after: Trip, isAdminEdit: Boolean) {
        if (isAdminEdit) {
            val edit = adminEdit(before, AdminEdit.Field.CATEGORY, before.category?.name, after.category?.name)
            save(after.copy(reason = ClassifyReason.ADMIN_EDIT), edit)
        } else {
            save(after)
        }
    }

    /**
     * 운행을 고쳐 쓴다. 관리자 수정이면 바꾸기 전·후 기록도 같은 트랜잭션에 넣는다.
     * 따로 쓰면 운행만 바뀌고 기록이 실패했을 때 "다시 하기"가 "이미 바뀜"으로 끝나 기록이 영영 빠진다.
     */
    private suspend fun save(trip: Trip, adminEdit: AdminEdit? = null) = write("운행 고치기") {
        db.withTransaction {
            tripDao.update(TripEntity.from(trip))
            adminEdit?.let { adminEditDao.insert(AdminEditEntity.from(it)) }
        }
    }

    private fun adminEdit(trip: Trip, field: AdminEdit.Field, from: String?, to: String?) =
        AdminEdit(trip.id, trip.date, field, from, to, LocalDateTime.now(clock))

    private inline fun <T> write(what: String, block: () -> T): T = writeOrThrow(TAG, what, block)

    /** 개인으로 바꾸기 전 분류·장소. 확정 전까지만 메모리에 둔다 */
    private data class PlaceUndo(
        val tripId: Long,
        val category: Category?,
        val reason: ClassifyReason?,
        val origin: TripPlace?,
        val destination: TripPlace?,
    )

    private companion object {
        const val TAG = "TripRepository"
    }
}
