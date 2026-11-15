package kr.chagok.car

import kotlinx.coroutines.flow.StateFlow

/** 차에서 받는 값: 계기판 누적거리, 기어, 동승자 수, 연료·배터리 잔량, 차대번호 */
interface VehicleSource {
    /** 계기판 누적거리 (km, 서식 ⑤⑥). 받기 전에는 null */
    val odometerKm: StateFlow<Double?>

    val gear: StateFlow<Gear>

    /** 운전석을 뺀 자리에 앉은 사람 수. 어느 자리 값도 아직 못 받았으면 null */
    val passengerCount: StateFlow<Int?>

    /** 연료 잔량 (%). 전기차이거나 아직 못 받았으면 null */
    val fuelPercent: StateFlow<Int?>

    /** 구동 배터리 충전량 SOC (%). 내연기관차이거나 아직 못 받았으면 null */
    val batteryPercent: StateFlow<Int?>

    fun start()

    fun stop()

    /** 못 읽으면 null */
    suspend fun readVin(): String?
}

enum class Gear {
    UNKNOWN,
    PARK,
    REVERSE,
    NEUTRAL,
    DRIVE;

    /** P단이 아니면 주행 중으로 본다 (신호 대기 D·N 포함). Pleos 가 주차로 보는 기준과 같다 */
    val isDriving: Boolean get() = this != PARK && this != UNKNOWN
}
