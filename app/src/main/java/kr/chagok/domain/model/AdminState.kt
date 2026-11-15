package kr.chagok.domain.model

import kr.chagok.domain.ChagokConfig
import java.time.LocalDate

/**
 * 관리자 PIN 잠금 상태.
 * PIN 을 정하지 않은 차는 모두 열려 있다. PIN 을 정한 차에서 운전자는 오늘 운행만 보고 고친다.
 */
data class AdminState(
    val hasPin: Boolean = false,
    val isUnlocked: Boolean = false,
    val triesLeft: Int = 0,
) {
    /** 지난 기록·내보내기·기록 방식을 쓸 수 있는지: 관리자가 없거나 지금 풀려 있으면 */
    val canManage: Boolean get() = !hasPin || isUnlocked

    /** 고친 것이 "관리자 수정"으로 남는지 */
    val isAdminEdit: Boolean get() = hasPin && isUnlocked

    /** PIN 을 너무 많이 틀려 복구키로만 열 수 있다 */
    val isPinBlocked: Boolean get() = hasPin && triesLeft <= 0

    /** 운행 하나를 열어 보고 고칠 수 있는지. 운전자도 오늘 운행은 된다 */
    fun canOpen(trip: Trip, today: LocalDate): Boolean = canManage || trip.date == today

    companion object {
        /** 저장된 관리자 정보를 읽지 못했을 때. "PIN 없음(다 열림)"이 아니라 잠긴 쪽으로 본다 */
        val UNREADABLE = AdminState(hasPin = true, isUnlocked = false, triesLeft = ChagokConfig.PIN_MAX_TRIES)
    }
}
