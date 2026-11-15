package kr.chagok.ui.guide

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kr.chagok.AppContainer
import kr.chagok.R
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ChagokSettings
import kr.chagok.ui.components.SheetOverlay
import kr.chagok.ui.containerFactory
import kr.chagok.ui.theme.TextSizes

/**
 * 홈 시작 가이드의 "해 보기": 첫 실행에서 건너뛴 "말해 보기" 연습을 시트로 한다.
 * 닫으면 연습으로 바꾼 다음 출발을 되돌린다.
 */
@Composable
fun TrySheet(
    isWide: Boolean,
    onDismiss: () -> Unit,
    viewModel: TryViewModel = viewModel(factory = TryViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        viewModel.start()
        onDispose { viewModel.end() }
    }
    val ui = state ?: return
    val title = if (ui.hasGleo) R.string.onboarding_try_title else R.string.onboarding_try_tap_title
    SheetOverlay(
        onDismiss = onDismiss,
        header = { Text(stringResource(title), fontSize = TextSizes.title, fontWeight = FontWeight.Bold) },
    ) {
        TryContent(
            practice = ui.practice,
            defaultCategory = ui.settings.defaultCategory,
            hasGleo = ui.hasGleo,
            isShared = ui.settings.usage.isShared,
            isWide = isWide,
            revertNote = stringResource(R.string.try_revert_on_close),
            onTap = viewModel::tap,
        )
    }
}

class TryViewModel(private val container: AppContainer) : ViewModel() {
    private val practice = ChangePractice(container.nextDeparture, container.settings, viewModelScope)

    // 기본 분류를 읽기 전에 시트를 닫으면 end 가 먼저 돌고 연습이 남는다. 닫을 때 시작을 함께 취소한다
    private var startJob: Job? = null

    val state: StateFlow<TryUiState?> = combine(container.settings.settings, practice.state) { settings, current ->
        TryUiState(settings, current, container.isGleoAvailable)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 시트가 열릴 때. 기본 분류를 읽은 뒤에 시작해야 반대쪽으로 바꿔 보게 할 수 있다 */
    fun start() {
        startJob?.cancel()
        startJob = viewModelScope.launch { practice.start(container.settings.settings.first().defaultCategory) }
    }

    /** 시트가 닫힐 때 (ViewModel 은 화면에 남아 다음에 열면 다시 [start]) */
    fun end() {
        startJob?.cancel()
        practice.end()
    }

    fun tap(category: Category) = practice.tap(category)

    override fun onCleared() = practice.end()

    companion object {
        val Factory = containerFactory(::TryViewModel)
    }
}

data class TryUiState(val settings: ChagokSettings, val practice: PracticeState?, val hasGleo: Boolean)
