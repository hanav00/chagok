package kr.chagok

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kr.chagok.domain.ChagokConfig
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 오늘 날짜. 자정이 지나면 새 날짜를 낸다. 차가 대기 상태로 화면을 켜 둔 채 자정을 넘겨도
 * "오늘"·이번 달·사업연도가 바뀌게 화면은 날짜를 여기서 받는다.
 * 기기가 깊이 잠들면 기다리는 시간도 멈출 수 있어, 화면이 다시 보일 때도 [refresh] 한다 (MainActivity).
 */
class Today(private val clock: Clock, scope: CoroutineScope) {
    private val _date = MutableStateFlow(LocalDate.now(clock))

    val date: StateFlow<LocalDate> = _date.asStateFlow()

    init {
        // 기다리기 전에 먼저 다시 읽는다. 만든 뒤 처음 돌기 전에 자정이 지났으면 다음 자정까지 기다리게 된다
        scope.launch {
            while (true) {
                refresh()
                delay(millisUntilNextCheck())
            }
        }
    }

    /** 지금 시계로 날짜를 다시 읽는다. 같은 날이면 아무 일도 없다 */
    fun refresh() {
        _date.value = LocalDate.now(clock)
    }

    // 다음 자정을 조금 넘긴 때. 시스템 시계가 바뀌는 경우를 생각해 아무리 길어도 정해 둔 간격마다 다시 본다
    private fun millisUntilNextCheck(): Long {
        val now = LocalDateTime.now(clock)
        val untilMidnight = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis()
        return (untilMidnight + ChagokConfig.MIDNIGHT_MARGIN_MILLIS).coerceAtMost(ChagokConfig.DAY_CHECK_MAX_MILLIS)
    }
}
