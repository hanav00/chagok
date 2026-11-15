package kr.chagok.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kr.chagok.R
import kr.chagok.domain.model.Category
import kr.chagok.ui.guide.TryContent

/** 말해 보기: Gleo 에게 실제로 말하면 다음 출발이 바뀌고 완료 표시가 뜬다. Gleo 가 없는 차는 토글 1탭 */
@Composable
fun TryStep(state: OnboardingUiState, isWide: Boolean, onTap: (Category) -> Unit) {
    val (title, sub) = if (state.hasGleo) {
        R.string.onboarding_try_title to R.string.onboarding_try_sub
    } else {
        R.string.onboarding_try_tap_title to R.string.onboarding_try_tap_sub
    }
    StepHeading(stringResource(title), stringResource(sub), isWide)
    TryContent(
        practice = state.practice,
        defaultCategory = state.defaultCategory,
        hasGleo = state.hasGleo,
        isShared = state.usage?.isShared == true,
        isWide = isWide,
        revertNote = stringResource(R.string.try_revert_on_finish),
        onTap = onTap,
    )
}
