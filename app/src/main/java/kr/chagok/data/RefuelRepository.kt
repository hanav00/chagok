package kr.chagok.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kr.chagok.data.db.EnergyMarkEntity
import kr.chagok.data.db.ChagokDatabase
import kr.chagok.data.db.RefuelEntity
import kr.chagok.domain.EnergyMark
import kr.chagok.domain.Fill
import kr.chagok.domain.ChagokConfig
import kr.chagok.domain.model.EnergyKind
import kr.chagok.domain.model.Refuel
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

/** 주유·충전 기록과 잔량 기준점 */
class RefuelRepository(db: ChagokDatabase, scope: CoroutineScope) {
    private val dao = db.refuelDao()
    private val editLock = Mutex()

    val refuels: StateFlow<List<Refuel>> = dao.observeAll()
        .map { list -> list.map(RefuelEntity::toDomain) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun mark(kind: EnergyKind): EnergyMark? =
        readOrDefault(TAG, "잔량 기준점 읽기", fallback = null) { dao.mark(kind)?.toDomain() }

    suspend fun saveMark(kind: EnergyKind, mark: EnergyMark) = write("잔량 기준점 저장") {
        dao.saveMark(EnergyMarkEntity.from(kind, mark))
    }

    /**
     * 알아챈 주유·충전을 남긴다. 같은 자리(계기판이 그대로)에서 이어 오른 값이면 앞 기록의 끝 잔량만 올린다.
     * 새로 남겼으면 true (안내는 한 번만 한다)
     */
    suspend fun recordFill(kind: EnergyKind, fill: Fill, odometerKm: Double, date: LocalDate, time: LocalTime): Boolean =
        editLock.withLock {
            write("주유·충전 저장") {
                val sameStop = dao.last(kind)?.toDomain()
                    ?.takeIf { abs(it.odometerKm - odometerKm) <= ChagokConfig.KM_EPSILON }
                if (sameStop != null) {
                    if (fill.toPercent > sameStop.toPercent) {
                        dao.update(RefuelEntity.from(sameStop.copy(toPercent = fill.toPercent)))
                    }
                    false
                } else {
                    val refuel = Refuel(
                        date = date,
                        time = time,
                        odometerKm = odometerKm,
                        kind = kind,
                        fromPercent = fill.fromPercent,
                        toPercent = fill.toPercent,
                    )
                    dao.insert(RefuelEntity.from(refuel))
                    true
                }
            }
        }

    suspend fun setAmount(id: Long, won: Long) = edit(id) { it.copy(amountWon = won, isAmountSkipped = false) }

    /** 금액을 모른다고 넘긴다 (법인카드 내역으로 따로 맞출 때) */
    suspend fun skipAmount(id: Long) = edit(id) { it.copy(isAmountSkipped = true) }

    private suspend fun edit(id: Long, change: (Refuel) -> Refuel) = editLock.withLock {
        write("주유·충전 고치기") {
            val refuel = dao.find(id)?.toDomain() ?: return@write
            dao.update(RefuelEntity.from(change(refuel)))
        }
    }

    private inline fun <T> write(what: String, block: () -> T): T = writeOrThrow(TAG, what, block)

    private companion object {
        const val TAG = "RefuelRepository"
    }
}
