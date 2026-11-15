package kr.chagok.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * 주유·충전 한 번. 차가 연료·배터리 잔량이 오른 것을 보고 남기고, 금액은 운전자가 말해서 채운다.
 * 법인은 유류비를 운행기록과 같이 정산하고, 명세서의 유류비 칸도 여기서 나온다.
 *
 * @property odometerKm 넣은 자리의 계기판 누적거리. 차가 서 있는 동안 더 오른 값은 같은 주유로 본다
 * @property amountWon 운전자가 말한 금액 (원). 아직 안 말했으면 null
 * @property isAmountSkipped 금액을 모른다고 넘긴 것 (법인카드 내역으로 따로 맞출 때). 홈 할 일에서 빠진다
 */
data class Refuel(
    val id: Long = 0,
    val uid: String = RecordUid.new(),
    val date: LocalDate,
    val time: LocalTime,
    val odometerKm: Double,
    val kind: EnergyKind,
    val fromPercent: Int,
    val toPercent: Int,
    val amountWon: Long? = null,
    val isAmountSkipped: Boolean = false,
) {
    /** 홈에서 금액을 물을 것 */
    val needsAmount: Boolean get() = amountWon == null && !isAmountSkipped
}

/** 연료(주유) 또는 구동 배터리(충전). 하이브리드는 둘 다 따로 본다 */
enum class EnergyKind { FUEL, CHARGE }
