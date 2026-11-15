package kr.chagok.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kr.chagok.data.db.ChagokDatabase
import kr.chagok.data.db.TripEntity
import kr.chagok.domain.model.ActiveTrip
import kr.chagok.domain.model.AdminEdit
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.domain.model.NextDeparture
import kr.chagok.domain.model.Trip
import kr.chagok.testing.MutableClock
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TripFixtures
import kr.chagok.testing.TripFixtures.CLIENT
import kr.chagok.testing.TripFixtures.OFFICE
import kr.chagok.testing.TripFixtures.TODAY
import kr.chagok.testing.inMemoryDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 운행 저장소.
 * 메모리 Room 위에서 실제 [TripRepository] 를 돌린다.
 */
@RunWith(RobolectricTestRunner::class)
class TripRepositoryTest {
    private val clock = MutableClock()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var db: ChagokDatabase
    private lateinit var repo: TripRepository

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repo = TripRepository(db, clock, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    /** 고칠 대상 운행을 DB 에 바로 넣고 저장된 모습을 돌려준다 (넣는 길은 시험 대상이 아니다) */
    private suspend fun stored(trip: Trip): Trip {
        val id = db.tripDao().insert(TripEntity.from(trip))
        return requireNotNull(repo.find(id))
    }

    private suspend fun reloaded(trip: Trip): Trip = requireNotNull(repo.find(trip.id))

    private suspend fun adminEdits(): List<AdminEdit> = repo.adminEdits.first()

    @Test
    fun `운행을 가져오면 기존 기록 뒤에 넣은 순서대로 붙고 id 는 새로 매긴다`() = runBlocking {
        stored(TripFixtures.trip(startKm = 0.0, endKm = 10.0))

        repo.importTrips(
            listOf(
                TripFixtures.trip(startKm = 10.0, endKm = 30.0, id = 77),
                TripFixtures.trip(startKm = 30.0, endKm = 35.0, category = Category.PERSONAL, id = 3),
            ),
        )

        val trips = await(repo.trips) { it.size == 3 }
        assertEquals(listOf(0.0, 10.0, 30.0), trips.map { it.startKm })
        assertEquals(listOf(1L, 2L, 3L), trips.map { it.id })
        assertEquals(Category.PERSONAL, trips.last().category)
    }

    @Test
    fun `기록이 없으면 마지막 도착 계기판은 없고, 있으면 가장 나중에 저장한 운행의 도착 값이다`() = runBlocking {
        assertNull(repo.lastEndKm())

        stored(TripFixtures.trip(startKm = 100.0, endKm = 110.0))
        stored(TripFixtures.trip(startKm = 110.0, endKm = 125.5))

        assertEquals(125.5, repo.lastEndKm()!!, 0.0)
    }

    @Test
    fun `기록 안 된 주행은 거리만 남고 시각·장소·사람·분류는 비어 있다`() = runBlocking {
        val id = repo.addGap(TODAY, startKm = 125.5, endKm = 133.7)

        val gap = repo.find(id)!!
        assertTrue(gap.isGap)
        assertTrue(gap.isPending)
        assertEquals(TODAY, gap.date)
        assertEquals(8.2, gap.km, 1e-9)
        assertNull(gap.startedAt)
        assertNull(gap.endedAt)
        assertNull(gap.origin)
        assertNull(gap.destination)
        assertNull(gap.personId)
        assertNull(gap.reason)
        assertFalse("기록 안 된 주행은 사람을 묻지 않는다", gap.isUnassigned)
        assertEquals(133.7, repo.lastEndKm()!!, 0.0)
    }

    @Test
    fun `업무 운행을 개인으로 바꾸면 출발지·도착지가 지워지고 근거는 사용자 수정, 장소를 되살릴 수 있는 운행이 된다`() = runBlocking {
        val trip = stored(TripFixtures.trip(category = Category.BUSINESS))

        repo.classify(trip.id, Category.PERSONAL)

        val personal = reloaded(trip)
        assertEquals(Category.PERSONAL, personal.category)
        assertEquals(ClassifyReason.USER_EDIT, personal.reason)
        assertNull(personal.origin)
        assertNull(personal.destination)
        assertEquals("거리는 그대로", trip.km, personal.km, 0.0)
        assertEquals(trip.id, await(repo.restorableTripId) { it != null })
    }

    @Test
    fun `개인으로 바꾼 직후 다시 업무로 바꾸면 장소와 원래 근거(기본값 업무)가 돌아온다`() = runBlocking {
        val trip = stored(TripFixtures.trip(category = Category.BUSINESS))
        repo.classify(trip.id, Category.PERSONAL)

        repo.classify(trip.id, Category.BUSINESS)

        assertEquals(trip, reloaded(trip))
        assertNull(await(repo.restorableTripId) { it == null })
    }

    @Test
    fun `개인으로 바꾼 뒤 원래와 다른 업무 분류(출퇴근)로 바꾸면 장소는 돌아오고 근거는 사용자 수정`() = runBlocking {
        val trip = stored(TripFixtures.trip(category = Category.BUSINESS))
        repo.classify(trip.id, Category.PERSONAL)

        repo.classify(trip.id, Category.COMMUTE)

        val commute = reloaded(trip)
        assertEquals(Category.COMMUTE, commute.category)
        assertEquals(ClassifyReason.USER_EDIT, commute.reason)
        assertEquals(OFFICE, commute.origin)
        assertEquals(CLIENT, commute.destination)
    }

    @Test
    fun `업무 운행을 출퇴근으로 바꾸면 장소는 그대로이고 근거는 사용자 수정`() = runBlocking {
        val trip = stored(TripFixtures.trip(category = Category.BUSINESS))

        repo.classify(trip.id, Category.COMMUTE)

        assertEquals(trip.copy(category = Category.COMMUTE, reason = ClassifyReason.USER_EDIT), reloaded(trip))
    }

    @Test
    fun `방금 개인으로 바꾼 운행에 '이건 업무였어요'를 누르면 원래 분류·장소·근거로 돌아간다`() = runBlocking {
        val trip = stored(TripFixtures.trip(category = Category.COMMUTE, reason = ClassifyReason.PRE_PICK))
        repo.classify(trip.id, Category.PERSONAL)

        repo.revertToWork(trip.id)

        assertEquals(trip, reloaded(trip))
        assertNull(await(repo.restorableTripId) { it == null })
    }

    @Test
    fun `처음부터 개인으로 기록된 운행에 '이건 업무였어요'를 누르면 일반 업무·사용자 수정이 되고 장소는 없다`() = runBlocking {
        val trip = stored(TripFixtures.trip(category = Category.PERSONAL))

        repo.revertToWork(trip.id)

        val business = reloaded(trip)
        assertEquals(Category.BUSINESS, business.category)
        assertEquals(ClassifyReason.USER_EDIT, business.reason)
        assertNull(business.origin)
        assertNull(business.destination)
    }

    @Test
    fun `장소 삭제를 확정하면 다시 업무로 바꿔도 장소는 돌아오지 않는다`() = runBlocking {
        val trip = stored(TripFixtures.trip(category = Category.BUSINESS))
        repo.classify(trip.id, Category.PERSONAL)
        await(repo.restorableTripId) { it == trip.id }

        repo.commitPlaceDeletion()
        repo.revertToWork(trip.id)

        val business = reloaded(trip)
        assertNull(await(repo.restorableTripId) { it == null })
        assertEquals(Category.BUSINESS, business.category)
        assertEquals("원래 근거(기본값 업무)가 아니라 사용자 수정", ClassifyReason.USER_EDIT, business.reason)
        assertNull(business.origin)
        assertNull(business.destination)
    }

    @Test
    fun `다른 운행의 장소 삭제를 확정해도 방금 개인으로 바꾼 운행은 아직 되살릴 수 있다 (다른 운행 시트를 열었다 닫음)`() = runBlocking {
        val recent = stored(TripFixtures.trip(startKm = 100.0, endKm = 110.0))
        val other = stored(TripFixtures.trip(startKm = 110.0, endKm = 120.0))
        repo.classify(recent.id, Category.PERSONAL)
        await(repo.restorableTripId) { it == recent.id }

        repo.commitPlaceDeletion(other.id)
        repo.revertToWork(recent.id)

        assertEquals(recent, reloaded(recent))
    }

    @Test
    fun `다른 운행을 개인으로 바꾸면 앞 운행은 더 되살릴 수 없다 (되살리기는 한 운행만)`() = runBlocking {
        val first = stored(TripFixtures.trip(startKm = 100.0, endKm = 110.0))
        val second = stored(TripFixtures.trip(startKm = 110.0, endKm = 120.0))
        repo.classify(first.id, Category.PERSONAL)

        repo.classify(second.id, Category.PERSONAL)
        repo.classify(first.id, Category.BUSINESS)

        assertEquals(second.id, await(repo.restorableTripId) { it == second.id })
        assertNull(reloaded(first).destination)
        assertEquals(ClassifyReason.USER_EDIT, reloaded(first).reason)
    }

    @Test
    fun `기록 안 된 주행을 고르면 근거는 사용자 선택이고 장소는 없다`() = runBlocking {
        val gapId = repo.addGap(TODAY, startKm = 100.0, endKm = 108.0)

        repo.classify(gapId, Category.BUSINESS)

        val picked = repo.find(gapId)!!
        assertEquals(Category.BUSINESS, picked.category)
        assertEquals(ClassifyReason.USER_PICK, picked.reason)
        assertNull(picked.origin)
        assertNull(picked.destination)
    }

    @Test
    fun `기록 안 된 주행은 개인으로 골랐다가 업무로 바꿔도 되살릴 장소가 없어 계속 사용자 선택이다`() = runBlocking {
        val gapId = repo.addGap(TODAY, startKm = 100.0, endKm = 108.0)

        repo.classify(gapId, Category.PERSONAL)
        repo.classify(gapId, Category.BUSINESS)

        val picked = repo.find(gapId)!!
        assertEquals(Category.BUSINESS, picked.category)
        assertEquals(ClassifyReason.USER_PICK, picked.reason)
        assertNull(picked.destination)
    }

    @Test
    fun `같은 분류로 바꾸면 근거도 수정 기록도 바뀌지 않는다`() = runBlocking {
        val trip = stored(TripFixtures.trip(category = Category.BUSINESS))

        repo.classify(trip.id, Category.BUSINESS)
        repo.classify(trip.id, Category.BUSINESS, isAdminEdit = true)

        assertEquals(trip, reloaded(trip))
        assertEquals(emptyList<AdminEdit>(), adminEdits())
    }

    @Test
    fun `없는 운행을 고치려 하면 아무 일도 일어나지 않는다`() = runBlocking {
        repo.classify(999, Category.PERSONAL, isAdminEdit = true)
        repo.revertToWork(999)
        repo.assignPerson(999, personId = 2, isAdminEdit = true)
        repo.setNote(999, "메모")

        assertNull(repo.find(999))
        assertEquals(emptyList<AdminEdit>(), adminEdits())
    }

    @Test
    fun `관리자가 분류를 바꾸면 근거는 관리자 수정이고 바꾸기 전·후와 고친 시각이 기록된다`() = runBlocking {
        val trip = stored(TripFixtures.trip(category = Category.BUSINESS))

        repo.classify(trip.id, Category.PERSONAL, isAdminEdit = true)

        val personal = reloaded(trip)
        assertEquals(Category.PERSONAL, personal.category)
        assertEquals(ClassifyReason.ADMIN_EDIT, personal.reason)
        assertNull("관리자가 바꿔도 개인 운행은 위치를 남기지 않는다", personal.destination)
        assertEquals(
            listOf(
                AdminEdit(
                    trip.id, TODAY, AdminEdit.Field.CATEGORY, "BUSINESS", "PERSONAL",
                    LocalDateTime.of(2026, 10, 26, 9, 0),
                ),
            ),
            adminEdits(),
        )
    }

    @Test
    fun `관리자가 개인으로 바꿨다가 되돌려도 근거는 관리자 수정이고 수정 기록은 최근 것부터 두 줄 남는다`() = runBlocking {
        val trip = stored(TripFixtures.trip(category = Category.BUSINESS))
        repo.classify(trip.id, Category.PERSONAL, isAdminEdit = true)

        repo.classify(trip.id, Category.BUSINESS, isAdminEdit = true)

        val restored = reloaded(trip)
        assertEquals(ClassifyReason.ADMIN_EDIT, restored.reason)
        assertEquals("장소는 되살아난다", CLIENT, restored.destination)
        assertEquals(
            listOf("PERSONAL" to "BUSINESS", "BUSINESS" to "PERSONAL"),
            adminEdits().map { it.fromValue to it.toValue },
        )
    }

    @Test
    fun `관리자가 개인으로 바꾼 운행에 '이건 업무였어요'를 눌러도 관리자 수정으로 남는다`() = runBlocking {
        val trip = stored(TripFixtures.trip(category = Category.BUSINESS))
        repo.classify(trip.id, Category.PERSONAL, isAdminEdit = true)

        repo.revertToWork(trip.id, isAdminEdit = true)

        val restored = reloaded(trip)
        assertEquals(ClassifyReason.ADMIN_EDIT, restored.reason)
        assertEquals(CLIENT, restored.destination)
        assertEquals(
            listOf("PERSONAL" to "BUSINESS", "BUSINESS" to "PERSONAL"),
            adminEdits().map { it.fromValue to it.toValue },
        )
    }

    @Test
    fun `관리자가 분류 대기인 기록 안 된 주행을 고르면 바꾸기 전 값은 비어 있다`() = runBlocking {
        val gapId = repo.addGap(TODAY, startKm = 100.0, endKm = 108.0)

        repo.classify(gapId, Category.COMMUTE, isAdminEdit = true)

        assertEquals(ClassifyReason.ADMIN_EDIT, repo.find(gapId)!!.reason)
        val edit = adminEdits().single()
        assertNull(edit.fromValue)
        assertEquals("COMMUTE", edit.toValue)
    }

    @Test
    fun `관리자가 아니면 분류를 바꿔도 수정 기록이 남지 않는다`() = runBlocking {
        val trip = stored(TripFixtures.trip(category = Category.BUSINESS))

        repo.classify(trip.id, Category.PERSONAL)

        assertEquals(emptyList<AdminEdit>(), adminEdits())
    }

    @Test
    fun `미지정 운행에 사람을 고르면 사람만 정해지고 분류 근거와 수정 기록은 그대로다`() = runBlocking {
        val trip = stored(TripFixtures.trip(personId = null))
        assertTrue(trip.isUnassigned)

        repo.assignPerson(trip.id, personId = 2)

        assertEquals(trip.copy(personId = 2), reloaded(trip))
        assertEquals(emptyList<AdminEdit>(), adminEdits())
    }

    @Test
    fun `관리자가 정해진 사람을 바꾸면 사람 id 전·후가 기록되지만 분류 근거는 바뀌지 않는다`() = runBlocking {
        val trip = stored(TripFixtures.trip(personId = 1))

        repo.assignPerson(trip.id, personId = 2, isAdminEdit = true)

        val changed = reloaded(trip)
        assertEquals(2L, changed.personId)
        assertEquals(ClassifyReason.DEFAULT_BUSINESS, changed.reason)
        val edit = adminEdits().single()
        assertEquals(AdminEdit.Field.PERSON, edit.field)
        assertEquals("1", edit.fromValue)
        assertEquals("2", edit.toValue)
    }

    @Test
    fun `같은 사람을 다시 고르면 수정 기록이 남지 않는다`() = runBlocking {
        val trip = stored(TripFixtures.trip(personId = 1))

        repo.assignPerson(trip.id, personId = 1, isAdminEdit = true)

        assertEquals(emptyList<AdminEdit>(), adminEdits())
    }

    @Test
    fun `비고를 적으면 그 운행에만 저장되고 분류는 그대로다`() = runBlocking {
        val trip = stored(TripFixtures.trip(startKm = 100.0, endKm = 110.0))
        val other = stored(TripFixtures.trip(startKm = 110.0, endKm = 120.0))

        repo.setNote(trip.id, "○○전자 납품 미팅")

        assertEquals(trip.copy(note = "○○전자 납품 미팅"), reloaded(trip))
        assertEquals("", reloaded(other).note)
    }

    private fun activeTrip(km: Double = 1_000.0) = ActiveTrip(
        date = TODAY,
        startKm = km,
        startedAt = LocalTime.of(9, 0),
        lastKm = km,
        lastAt = LocalTime.of(9, 0),
        departure = NextDeparture(Category.PERSONAL, isChanged = true, personId = 2),
        origin = OFFICE,
        destination = CLIENT,
    )

    @Test
    fun `진행 중 운행이 없으면 null 이고, 출발 때 연 운행은 분류·사람·장소 그대로 다시 읽힌다`() = runBlocking {
        assertNull(repo.activeTrip())

        repo.saveActiveTrip(activeTrip())

        assertEquals(activeTrip(), repo.activeTrip())
    }

    @Test
    fun `진행 중 운행을 다시 저장하면 한 줄을 덮어쓴다 (주행 중 내비 목적지)`() = runBlocking {
        repo.saveActiveTrip(activeTrip().copy(destination = null))

        repo.saveActiveTrip(activeTrip())

        assertEquals(CLIENT, repo.activeTrip()!!.destination)
        assertEquals(1, db.query("SELECT * FROM active_trip", null).use { it.count })
    }

    @Test
    fun `계기판 저장은 마지막 거리·시각만 덮어쓰고 출발 값은 그대로 둔다`() = runBlocking {
        repo.saveActiveTrip(activeTrip(km = 1_000.0))

        repo.checkpoint(km = 1_004.3, at = LocalTime.of(9, 12, 30))

        assertEquals(activeTrip().copy(lastKm = 1_004.3, lastAt = LocalTime.of(9, 12, 30)), repo.activeTrip())
    }

    @Test
    fun `진행 중 운행이 없을 때 계기판 저장은 아무것도 만들지 않는다`() = runBlocking {
        repo.checkpoint(km = 1_004.3, at = LocalTime.of(9, 12))

        assertNull(repo.activeTrip())
    }

    @Test
    fun `주차하면 운행이 기록부에 저장되고 진행 중 운행은 비워진다`() = runBlocking {
        repo.saveActiveTrip(activeTrip())
        val trip = TripFixtures.trip(startKm = 1_000.0, endKm = 1_008.8)

        val id = repo.closeActiveTrip(trip)

        assertEquals(trip.copy(id = id), repo.find(id))
        assertNull(repo.activeTrip())
        assertEquals(1_008.8, repo.lastEndKm()!!, 0.0)
        assertEquals(listOf(id), await(repo.trips) { it.isNotEmpty() }.map { it.id })
    }

    @Test
    fun `주차 저장이 실패하면 진행 중 운행도 지우지 않고 실패를 호출부에 알린다 (둘은 한 번에 처리된다)`() = runBlocking {
        val existing = stored(TripFixtures.trip(startKm = 900.0, endKm = 1_000.0))
        repo.saveActiveTrip(activeTrip())

        // 이미 있는 id 로 넣으면 운행 저장이 실패한다
        assertFails { repo.closeActiveTrip(TripFixtures.trip(startKm = 1_000.0, endKm = 1_008.8, id = existing.id)) }

        assertNotNull("진행 중 운행이 남아 다음 시작 때 다시 마감할 수 있다", repo.activeTrip())
        assertEquals(existing, reloaded(existing))
        assertEquals(1_000.0, repo.lastEndKm()!!, 0.0)
    }

    // 고치기가 실패하면 화면은 DB 를 그대로 보여 주므로 원래 값으로 남아야 한다

    /** 운행 고치기(UPDATE)를 막아 저장 실패를 만든다 */
    private fun failTripUpdates(isFailing: Boolean) {
        val sql = db.openHelper.writableDatabase
        if (isFailing) {
            sql.execSQL("CREATE TRIGGER fail_trip_update BEFORE UPDATE ON trips BEGIN SELECT RAISE(ABORT, 'test'); END")
        } else {
            sql.execSQL("DROP TRIGGER fail_trip_update")
        }
    }

    private suspend fun assertFails(block: suspend () -> Unit) {
        try {
            block()
            fail("저장 실패가 호출부로 넘어와야 한다 (화면이 다시 하기를 띄운다)")
        } catch (expected: Exception) {
            // 실패를 알린다
        }
    }

    @Test
    fun `분류·사람 지정 저장이 실패하면 운행은 원래 값 그대로다`() = runBlocking {
        val trip = stored(TripFixtures.trip(category = Category.BUSINESS, personId = null))
        failTripUpdates(true)

        assertFails { repo.classify(trip.id, Category.COMMUTE) }
        assertFails { repo.assignPerson(trip.id, personId = 7) }

        assertEquals(trip, reloaded(trip))
    }

    @Test
    fun `개인으로 바꾸기가 실패하면 장소를 되살릴 운행으로 남지 않는다`() = runBlocking {
        val trip = stored(TripFixtures.trip(category = Category.BUSINESS))
        failTripUpdates(true)

        assertFails { repo.classify(trip.id, Category.PERSONAL) }

        assertEquals(trip, reloaded(trip))
        assertNull(repo.restorableTripId.value)
    }

    @Test
    fun `업무로 되돌리기가 실패해도 지웠던 장소는 남아 다시 하면 돌아온다`() = runBlocking {
        val trip = stored(TripFixtures.trip(category = Category.COMMUTE, reason = ClassifyReason.PRE_PICK))
        repo.classify(trip.id, Category.PERSONAL)
        await(repo.restorableTripId) { it == trip.id }
        failTripUpdates(true)

        assertFails { repo.revertToWork(trip.id) }
        assertEquals(trip.id, repo.restorableTripId.value)

        failTripUpdates(false)
        repo.revertToWork(trip.id)
        assertEquals(trip, reloaded(trip))
    }

    /** 관리자 수정 기록(INSERT)을 막는다 */
    private fun failAdminEditLog(isFailing: Boolean) {
        val sql = db.openHelper.writableDatabase
        if (isFailing) {
            sql.execSQL("CREATE TRIGGER fail_admin_edit BEFORE INSERT ON admin_edits BEGIN SELECT RAISE(ABORT, 'test'); END")
        } else {
            sql.execSQL("DROP TRIGGER fail_admin_edit")
        }
    }

    @Test
    fun `관리자 수정 기록을 못 남기면 운행도 바뀌지 않아 다시 하면 운행과 기록이 함께 남는다`() = runBlocking {
        val trip = stored(TripFixtures.trip(category = Category.BUSINESS, personId = 1))
        failAdminEditLog(true)

        assertFails { repo.classify(trip.id, Category.PERSONAL, isAdminEdit = true) }
        assertFails { repo.assignPerson(trip.id, personId = 2, isAdminEdit = true) }
        assertEquals("기록 없이 운행만 바뀌면 안 된다", trip, reloaded(trip))

        failAdminEditLog(false)
        repo.classify(trip.id, Category.PERSONAL, isAdminEdit = true)
        repo.assignPerson(trip.id, personId = 2, isAdminEdit = true)

        assertEquals(ClassifyReason.ADMIN_EDIT, reloaded(trip).reason)
        assertEquals(listOf(AdminEdit.Field.PERSON, AdminEdit.Field.CATEGORY), adminEdits().map(AdminEdit::field))
    }
}
