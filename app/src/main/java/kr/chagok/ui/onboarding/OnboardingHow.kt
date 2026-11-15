package kr.chagok.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kr.chagok.R
import kr.chagok.ui.guide.HowContent

/** 이렇게 기록돼요: 고른 기본 분류로 견본 운행 하나가 흘러가는 것을 보여 준다 */
@Composable
fun HowStep(state: OnboardingUiState, isWide: Boolean, onSpeak: (String) -> Unit) {
    StepHeading(stringResource(R.string.onboarding_how_title), stringResource(R.string.onboarding_how_sub), isWide)
    HowContent(state.how, isWide, onSpeak)
}
