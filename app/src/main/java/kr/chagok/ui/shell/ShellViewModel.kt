package kr.chagok.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kr.chagok.AppContainer
import kr.chagok.domain.model.Category
import kr.chagok.ui.UserMessage
import kr.chagok.ui.WhileUiSubscribed
import kr.chagok.ui.containerFactory

/**
 * 앱 껍데기 상태: 첫 실행인지, 주행 중인지, 관리자 모드인지, 홈 탭 배지.
 * 탭·시트처럼 화면 안에서만 쓰는 상태는 [ChagokApp] 이 들고 있다.
 */
class ShellViewModel(private val container: AppContainer) : ViewModel() {

    // 잠긴 운전자가 고를 수 있는 것은 오늘 운행뿐이라, 자정이 지나면 배지도 다시 센다 (combine 은 다섯 개까지라 묶는다)
    private val adminAndToday = combine(container.admin.state, container.today.date, ::Pair)

    val state: StateFlow<ShellState?> = combine(
        container.settings.settings,
        container.recorder.isDriving,
        container.recorder.drivingCategory,
        adminAndToday,
        container.trips.trips,
    ) { settings, isDriving, category, (admin, today), trips ->
        // 배지는 지금 이 사람이 처리할 수 있는 것만 센다. 잠긴 지난 미지정까지 세면 운전자에게 영원히 줄지 않는 배지가 된다
        val unassigned = if (settings.usage.isShared) trips.count { it.isUnassigned && admin.canOpen(it, today) } else 0
        ShellState(
            isFirstRunDone = settings.isFirstRunDone,
            isDriving = isDriving,
            drivingCategory = category ?: settings.defaultCategory,
            isAdminUnlocked = admin.isUnlocked,
            badgeCount = trips.count { it.isPending } + unassigned,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, null)

    /**
     * 홈의 "방금 운행" 카드가 새로 뜰 때마다: 주차해서 운행이 저장됐을 때와 닫은 카드를 "다시 보기"로 열 때.
     * 받는 쪽은 홈으로 가고 운행 상세를 닫는다. 처음 구독할 때의 값은 건너뛴다.
     */
    val recentTripShown: Flow<Long> = container.recorder.recentTripId.drop(1).filterNotNull()

    /** 화면 아래 알림 줄로 띄울 것 (저장 실패·음성 못 알아들음). 받는 쪽은 껍데기 하나 */
    val messages: Flow<UserMessage> = container.messages.messages

    fun lockAdmin() = container.admin.lock()

    companion object {
        val Factory = containerFactory(::ShellViewModel)
    }
}

data class ShellState(
    val isFirstRunDone: Boolean,
    val isDriving: Boolean,
    /** 주행 중 화면에 보일 이번 운행 분류 */
    val drivingCategory: Category,
    val isAdminUnlocked: Boolean,
    /** 홈 탭 배지: 분류 대기 + 지금 고를 수 있는 사용자 미지정 (잠긴 지난 건은 빼고) */
    val badgeCount: Int,
)
