package kr.chagok.recorder

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kr.chagok.car.VehicleSource
import kr.chagok.common.runSuspendCatching
import kr.chagok.data.RefuelRepository
import kr.chagok.domain.RefuelRules
import kr.chagok.domain.model.EnergyKind
import kr.chagok.domain.model.ChagokSettings
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

/**
 * 연료·배터리 잔량을 지켜보다가 오르면 주유·충전으로 남기고 한 번 말한다 ("충전을 기록했어요. 금액은 화면에서 말해 주세요.").
 * 넣은 자리를 계기판 값으로 남기므로 계기판을 받기 전 값은 미뤘다가 계기판이 오면 본다.
 * 값은 하나씩 차례로 처리한다 (기준점을 읽고 쓰는 사이에 다음 값이 끼어들지 않게).
 */
class RefuelWatcher(
    private val vehicle: VehicleSource,
    private val refuels: RefuelRepository,
    private val settings: Flow<ChagokSettings>,
    private val tripVoice: TripVoice,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private var isStarted = false

    fun start() {
        if (isStarted) return
        isStarted = true
        scope.launch {
            merge(
                readings(EnergyKind.FUEL, vehicle.fuelPercent),
                readings(EnergyKind.CHARGE, vehicle.batteryPercent),
            ).collect { handle(it) }
        }
    }

    private fun readings(kind: EnergyKind, percent: Flow<Int?>): Flow<Reading> =
        combine(percent.filterNotNull(), vehicle.odometerKm.filterNotNull()) { value, km -> Reading(kind, value, km) }
            .distinctUntilChanged()

    private suspend fun handle(reading: Reading) {
        runSuspendCatching {
            val mark = refuels.mark(reading.kind)
            val isParked = !vehicle.gear.value.isDriving
            val (next, fill) = RefuelRules.next(mark, reading.percent, reading.odometerKm, isParked)
            if (next != mark) refuels.saveMark(reading.kind, next)
            if (fill == null) return@runSuspendCatching
            val now = LocalTime.now(clock)
            val isNew = refuels.recordFill(reading.kind, fill, reading.odometerKm, LocalDate.now(clock), now)
            if (isNew && settings.first().isTripVoiceOn) tripVoice.announceRefuel(reading.kind)
        }.onFailure { Log.e(TAG, "${reading.kind} 잔량 처리 실패", it) }
    }

    private data class Reading(val kind: EnergyKind, val percent: Int, val odometerKm: Double)

    private companion object {
        const val TAG = "RefuelWatcher"
    }
}
