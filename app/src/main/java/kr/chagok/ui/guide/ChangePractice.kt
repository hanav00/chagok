package kr.chagok.ui.guide

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kr.chagok.common.runSuspendCatching
import kr.chagok.data.NextDepartureStore
import kr.chagok.data.SettingsRepository
import kr.chagok.domain.model.Category

/**
 * "Gleo에게 말해 보기"·"1탭으로 바꿔 보기" 연습: 다음 출발을 기본 분류의 반대쪽으로 바꿔 보게 한다.
 * Gleo([kr.chagok.gleo.GleoCommands])로 바꾸든 토글을 누르든 다음 출발 값만 지켜본다.
 * 끝낼 때 바꾼 다음 출발을 되돌린다. 연습 때문에 실제 운행이 다른 분류로 기록되면 안 된다.
 */
class ChangePractice(
    private val departure: NextDepartureStore,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val target = MutableStateFlow<Category?>(null)

    val state: StateFlow<PracticeState?> = combine(target, departure.pickedCategory) { goal, picked ->
        goal?.let { PracticeState(target = it, picked = picked, isDone = picked == it) }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        // 해낸 순간 한 번 남긴다. 남기기가 실패해도 연습 화면은 완료 표시 그대로 (다음에 Gleo 를 쓰면 다시 남는다)
        scope.launch {
            state.collect { current ->
                if (current?.isDone == true) {
                    runSuspendCatching { settings.markChangeTried() }.onFailure { Log.e(TAG, "바꿔 보기 남기기 실패", it) }
                }
            }
        }
    }

    /**
     * 연습을 시작한다. 이전에 골라 둔 다음 출발은 비워서, 지금부터 바꾼 것만 센다.
     * 첫 실행에서 기본 분류를 바꾸고 돌아오면 목표가 달라지니 처음부터 다시 한다
     */
    fun start(defaultCategory: Category) {
        val goal = targetFor(defaultCategory)
        if (target.value == goal) return
        departure.clear()
        target.value = goal
    }

    /** Gleo 가 없는 차의 1탭 연습 */
    fun tap(category: Category) = departure.pickCategory(category)

    /** 첫 실행을 마치거나 시트를 닫을 때만 부른다. 단계를 오가는 동안에는 연습이 이어진다 */
    fun end() {
        if (target.value == null) return
        departure.clear()
        target.value = null
    }

    companion object {
        private const val TAG = "ChangePractice"

        /** 기본값과 다른 쪽으로 바꿔 봐야 바뀐 게 보인다 */
        fun targetFor(defaultCategory: Category): Category =
            if (defaultCategory == Category.PERSONAL) Category.BUSINESS else Category.PERSONAL
    }
}

/**
 * @property target 바꿔 볼 분류 (기본값의 반대)
 * @property picked 지금 다음 출발에 직접 고른 분류
 */
data class PracticeState(val target: Category, val picked: Category?, val isDone: Boolean)
