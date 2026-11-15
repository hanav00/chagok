package kr.chagok.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kr.chagok.car.Gear
import kr.chagok.car.VehicleSource

/** 가짜 차 (에뮬레이터 Virtual Console 자리). 기어·계기판·동승자·잔량을 테스트가 직접 바꾼다. 처음에는 혼자 탄 차다 */
class FakeVehicleSource(
    odometerKm: Double? = null,
    gear: Gear = Gear.PARK,
) : VehicleSource {
    override val odometerKm = MutableStateFlow(odometerKm)
    override val gear = MutableStateFlow(gear)
    override val passengerCount = MutableStateFlow<Int?>(0)
    override val fuelPercent = MutableStateFlow<Int?>(null)
    override val batteryPercent = MutableStateFlow<Int?>(null)

    override fun start() = Unit

    override fun stop() = Unit

    override suspend fun readVin(): String? = null

    fun shift(gear: Gear) {
        this.gear.value = gear
    }

    /** 계기판을 [km] 만큼 앞으로 */
    fun drive(km: Double) {
        odometerKm.value = (odometerKm.value ?: 0.0) + km
    }
}
