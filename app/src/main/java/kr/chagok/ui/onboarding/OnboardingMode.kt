package kr.chagok.ui.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Work
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kr.chagok.R
import kr.chagok.domain.model.Category
import kr.chagok.ui.components.Hint

/** 기본 분류: 업무(추천)·개인 중 고르고, 분류마다 위치를 남기는지 알린다 */
@Composable
fun ModeStep(state: OnboardingUiState, isWide: Boolean, onSelect: (Category) -> Unit) {
    StepHeading(stringResource(R.string.onboarding_mode_title), stringResource(R.string.onboarding_mode_sub), isWide)
    ChoiceCards(
        options = listOf(
            ChoiceOption(
                Category.BUSINESS,
                Icons.Rounded.Work,
                stringResource(R.string.category_business_short),
                badge = stringResource(R.string.onboarding_recommended),
            ),
            ChoiceOption(
                Category.PERSONAL,
                Icons.Rounded.DirectionsCar,
                stringResource(R.string.category_personal_short),
            ),
        ),
        selected = state.defaultCategory,
        onSelect = onSelect,
        isWide = isWide,
    )
    PromiseList(
        listOf(
            PromiseLine(
                Icons.Rounded.LocationOn,
                stringResource(R.string.onboarding_promise_business),
                stringResource(R.string.onboarding_promise_business_text),
            ),
            PromiseLine(
                Icons.Rounded.LocationOff,
                stringResource(R.string.onboarding_promise_personal),
                stringResource(R.string.onboarding_promise_personal_text),
                isOff = true,
            ),
        ),
    )
    // 기본값 업무를 그대로 두면 개인 운행이 업무로 잡힐 수 있다
    Hint(stringResource(R.string.onboarding_mode_responsibility), isWarning = true)
}
