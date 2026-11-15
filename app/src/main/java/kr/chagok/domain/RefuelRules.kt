package kr.chagok.domain

/**
 * 연료·배터리 잔량의 기준점. 잔량이 이보다 [ChagokConfig.REFUEL_MIN_RISE_PERCENT] 이상 오르면 주유·충전이다.
 * 주유를 알아챈 뒤 차가 움직이기 전까지는 [isFilling] 이라, 그동안 더 오르는 값(천천히 차는 충전)은 같은 주유로 본다.
 */
data class EnergyMark(val percent: Int, val odometerKm: Double, val isFilling: Boolean = false)

/** 이번 값으로 알아챈 주유·충전: [fromPercent] 에서 [toPercent] 까지 */
data class Fill(val fromPercent: Int, val toPercent: Int)

object RefuelRules {
    /**
     * 잔량 값 하나를 받아 다음 기준점과, 주유·충전이면 그 내용을 돌려준다.
     * 시동을 끈 채 넣으면 앱이 꺼져 있었으니 다시 켜진 뒤 처음 받은 값으로 알아챈다. 그래서 기준점은 저장해 둔다.
     * 달리는 중에 오른 값(회생 제동)은 주유가 아니다. 단, 앱이 꺼진 사이 달리고 넣었다면 주차 중에 받은 값으로 알아챈다.
     */
    fun next(mark: EnergyMark?, percent: Int, odometerKm: Double, isParked: Boolean): Pair<EnergyMark, Fill?> {
        if (mark == null) return EnergyMark(percent, odometerKm) to null
        val hasMoved = odometerKm - mark.odometerKm > ChagokConfig.KM_EPSILON
        if (mark.isFilling && hasMoved) return EnergyMark(percent, odometerKm) to null
        val rise = percent - mark.percent
        if (rise >= ChagokConfig.REFUEL_MIN_RISE_PERCENT && (isParked || !hasMoved)) {
            return mark.copy(odometerKm = odometerKm, isFilling = true) to Fill(mark.percent, percent)
        }
        val isLower = percent < mark.percent
        if (!mark.isFilling && (hasMoved || isLower)) return EnergyMark(percent, odometerKm) to null
        return mark to null
    }
}
