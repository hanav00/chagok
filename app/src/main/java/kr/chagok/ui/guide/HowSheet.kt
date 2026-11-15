package kr.chagok.ui.guide

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kr.chagok.AppContainer
import kr.chagok.R
import kr.chagok.ui.WhileUiSubscribed
import kr.chagok.ui.components.ActionButton
import kr.chagok.ui.components.ButtonStyle
import kr.chagok.ui.components.SheetOverlay
import kr.chagok.ui.containerFactory
import kr.chagok.ui.theme.TextSizes
import java.time.LocalDate

/**
 * 설정 "튜토리얼 다시 보기": 첫 실행 알아보기를 지금 설정(기본 분류)으로 다시 본다.
 * 설정 단계는 다시 하지 않는다. 바꿀 값은 설정에서 바로 바꾼다.
 *
 * @param onTry 말해 보기 연습 시트로 넘어가기
 */
@Composable
fun HowSheet(
    isWide: Boolean,
    onDismiss: () -> Unit,
    onTry: () -> Unit,
    viewModel: HowViewModel = viewModel(factory = HowViewModel.Factory),
) {
    val place = stringResource(R.string.onboarding_how_sample_place)
    LaunchedEffect(place) { viewModel.setPlace(place) }
    val story by viewModel.story.collectAsStateWithLifecycle()
    val ui = story ?: return
    SheetOverlay(
        onDismiss = onDismiss,
        header = {
            Text(stringResource(R.string.onboarding_how_title), fontSize = TextSizes.title, fontWeight = FontWeight.Bold)
        },
    ) {
        HowContent(ui, isWide, viewModel::speak)
        ActionButton(
            stringResource(if (ui.hasGleo) R.string.onboarding_done_try_gleo else R.string.onboarding_done_try_tap),
            onClick = onTry,
            icon = if (ui.hasGleo) Icons.Rounded.Mic else Icons.Rounded.TouchApp,
            style = ButtonStyle.Primary,
        )
    }
}

class HowViewModel(private val container: AppContainer) : ViewModel() {
    // 견본 도착지. 문자열 리소스라 화면이 넘겨 준다 ([setPlace])
    private val place = MutableStateFlow<String?>(null)

    val story: StateFlow<HowStory?> = combine(container.settings.settings, place) { settings, name ->
        name?.let {
            HowStory.build(
                container.tripVoice,
                LocalDate.now(container.clock),
                settings.defaultCategory,
                container.isGleoAvailable,
                it,
            )
        }
    }.stateIn(viewModelScope, WhileUiSubscribed, null)

    fun setPlace(name: String) {
        place.value = name
    }

    fun speak(text: String) = container.tripVoice.preview(text)

    companion object {
        val Factory = containerFactory(::HowViewModel)
    }
}
