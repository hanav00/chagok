package kr.chagok.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AirlineSeatReclineExtra
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.ui.components.Avatar
import kr.chagok.ui.components.AvatarSize
import kr.chagok.ui.components.Hint
import kr.chagok.ui.theme.IconSizes
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

// 단계 막대의 "차" 묶음: 누가 쓰나요 · 사람 · 관리자

private const val PEOPLE_COLUMNS = 2
private const val PEOPLE_COLUMNS_WIDE = 4
private val PEOPLE_GAP = Spacing.s
private val LISTENING_BORDER = 3.dp
private val BUTTON_ROW_MAX_WIDE = 720.dp // 넓게에서 버튼 줄이 화면 끝까지 늘어나지 않게

@Composable
fun WhoStep(state: OnboardingUiState, isWide: Boolean, onSelect: (VehicleUsage) -> Unit) {
    StepHeading(stringResource(R.string.onboarding_who_title), stringResource(R.string.onboarding_who_sub), isWide)
    ChoiceCards(
        options = listOf(
            ChoiceOption(VehicleUsage.SOLO, Icons.Rounded.Person, stringResource(R.string.onboarding_usage_solo)),
            ChoiceOption(VehicleUsage.SHARED, Icons.Rounded.Group, stringResource(R.string.onboarding_usage_shared)),
            ChoiceOption(
                VehicleUsage.CHAUFFEUR,
                Icons.Rounded.AirlineSeatReclineExtra,
                stringResource(R.string.onboarding_usage_chauffeur),
            ),
        ),
        selected = state.usage,
        onSelect = onSelect,
        isWide = isWide,
    )
}

@Composable
fun PeopleStep(
    state: OnboardingUiState,
    labels: UnnamedLabels,
    isWide: Boolean,
    onVoiceAdd: () -> Unit,
    onPlainAdd: () -> Unit,
) {
    val title = if (state.usage == VehicleUsage.CHAUFFEUR) {
        R.string.onboarding_people_title_chauffeur
    } else {
        R.string.onboarding_people_title
    }
    StepHeading(stringResource(title), stringResource(R.string.onboarding_people_sub), isWide)

    val columns = if (isWide) PEOPLE_COLUMNS_WIDE else PEOPLE_COLUMNS
    Column(verticalArrangement = Arrangement.spacedBy(PEOPLE_GAP)) {
        state.people.indices.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(PEOPLE_GAP)) {
                row.forEach { index -> PersonCard(state, index, labels, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

    ButtonRow(isWide) {
        OnboardingButton(
            text = stringResource(R.string.onboarding_voice_add),
            onClick = onVoiceAdd,
            modifier = Modifier.weight(1f),
            style = OnboardingButtonStyle.Ghost,
            iconBefore = Icons.Rounded.Mic,
            enabled = state.canAddPerson,
        )
        OnboardingButton(
            text = stringResource(R.string.onboarding_plain_add),
            onClick = onPlainAdd,
            modifier = Modifier.weight(1f),
            style = OnboardingButtonStyle.Ghost,
            iconBefore = Icons.Rounded.PersonAdd,
            enabled = state.canAddPerson,
        )
    }
}

@Composable
private fun PersonCard(state: OnboardingUiState, index: Int, labels: UnnamedLabels, modifier: Modifier) {
    val isListening = state.listeningIndex == index
    Row(
        modifier = modifier
            .background(ChagokColors.card, Shapes.card)
            .then(if (isListening) Modifier.border(LISTENING_BORDER, ChagokColors.accent, Shapes.card) else Modifier)
            .padding(Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(state.personInitial(index, labels), size = AvatarSize.Medium)
        Text(
            text = if (isListening) stringResource(R.string.onboarding_listening) else state.personTitle(index, labels),
            fontSize = TextSizes.subtitle,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** 관리자 PIN (선택): 운전자·관리자가 할 수 있는 일을 두 줄로 보여 주고 PIN 시트를 연다 */
@Composable
fun AdminStep(state: OnboardingUiState, isWide: Boolean, onSetupPin: () -> Unit) {
    StepHeading(
        title = stringResource(R.string.onboarding_admin_title),
        sub = stringResource(R.string.onboarding_admin_sub),
        isWide = isWide,
        optional = stringResource(R.string.onboarding_optional),
    )
    PromiseList(
        listOf(
            PromiseLine(
                Icons.Rounded.Badge,
                stringResource(R.string.onboarding_role_driver),
                stringResource(R.string.onboarding_role_driver_text),
            ),
            PromiseLine(
                Icons.Rounded.AdminPanelSettings,
                stringResource(R.string.onboarding_role_admin),
                stringResource(R.string.onboarding_role_admin_text),
            ),
        ),
    )
    if (state.hasPin) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.VerifiedUser,
                contentDescription = null,
                tint = ChagokColors.businessText,
                modifier = Modifier.size(IconSizes.small),
            )
            Text(
                stringResource(R.string.onboarding_admin_done),
                color = ChagokColors.businessText,
                fontSize = TextSizes.subtitle,
                fontWeight = FontWeight.Bold,
            )
            OnboardingButton(
                stringResource(R.string.onboarding_admin_redo),
                onSetupPin,
                style = OnboardingButtonStyle.Plain,
            )
        }
    } else {
        ButtonRow(isWide) {
            OnboardingButton(
                text = stringResource(R.string.onboarding_admin_set),
                onClick = onSetupPin,
                modifier = Modifier.weight(1f),
                style = OnboardingButtonStyle.Ghost,
                iconBefore = Icons.Rounded.Pin,
            )
        }
        Hint(stringResource(R.string.onboarding_admin_hint))
    }
}

/** 버튼을 나란히 같은 너비로 놓는다 */
@Composable
private fun ButtonRow(isWide: Boolean, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = if (isWide) Modifier.widthIn(max = BUTTON_ROW_MAX_WIDE).fillMaxWidth() else Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        content = content,
    )
}
