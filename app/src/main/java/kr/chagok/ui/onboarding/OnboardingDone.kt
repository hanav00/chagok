package kr.chagok.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.domain.FirstRunResult
import kr.chagok.domain.model.TripPlace
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.ui.components.personName
import kr.chagok.ui.components.shortLabelRes
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

private val LABEL_WIDTH = 120.dp
private const val ROW_LINE_HEIGHT = 1.4f
private const val NAME_SEPARATOR = ", "

/** 정리: 실제로 적용될 값을 항목 | 값 두 칸으로 보여 준다 */
@Composable
fun DoneStep(state: OnboardingUiState, labels: UnnamedLabels, isWide: Boolean) {
    val title = if (state.isSkipped) R.string.onboarding_done_title_skipped else R.string.onboarding_done_title
    StepHeading(stringResource(title), stringResource(R.string.onboarding_done_sub), isWide)

    val rows = summaryRows(state, state.toChoice(labels).toResult())
    val style = LocalTextStyle.current.copy(lineHeight = TextSizes.body * ROW_LINE_HEIGHT)
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = ChagokColors.divider)
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) HorizontalDivider(color = ChagokColors.divider)
            Row(Modifier.padding(vertical = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(label, Modifier.width(LABEL_WIDTH), color = ChagokColors.textSub, style = style)
                Text(value, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, style = style)
            }
        }
        HorizontalDivider(color = ChagokColors.divider)
    }
}

/**
 * 정리 화면의 (항목, 값) 줄.
 * 혼자 쓰는 차에 PIN 도 없으면 관리자 줄은 뺀다. 집·회사는 둘 다 정해져야 쓰므로 하나라도 없으면 "집·회사 안 씀" 한 줄.
 */
@Composable
private fun summaryRows(state: OnboardingUiState, result: FirstRunResult): List<Pair<String, String>> {
    val names = result.people.map { personName(it.name, it.initial) }.joinToString(NAME_SEPARATOR)
    val usage = when (result.usage) {
        VehicleUsage.SOLO -> stringResource(R.string.onboarding_done_usage_solo)
        VehicleUsage.SHARED -> stringResource(R.string.onboarding_done_usage_shared, names)
        VehicleUsage.CHAUFFEUR -> stringResource(R.string.onboarding_done_usage_chauffeur, names)
    }
    val isAdminShown = result.usage != VehicleUsage.SOLO || state.hasPin
    val admin = stringResource(
        if (state.hasPin) R.string.onboarding_done_admin_set else R.string.onboarding_done_admin_none,
    )

    val rows = buildList {
        add(stringResource(R.string.onboarding_done_usage) to usage)
        if (isAdminShown) add(stringResource(R.string.onboarding_done_admin) to admin)
        add(stringResource(R.string.onboarding_done_mode) to stringResource(result.defaultCategory.shortLabelRes))
        val tryLabel = if (state.hasGleo) R.string.onboarding_done_try_gleo else R.string.onboarding_done_try_tap
        val tryValue =
            if (state.practice?.isDone == true) R.string.onboarding_done_tried else R.string.onboarding_done_try_later
        add(stringResource(tryLabel) to stringResource(tryValue))
    }
    val home = result.home
    val office = result.office
    if (home == null || office == null) {
        return rows +
            (stringResource(R.string.onboarding_done_places) to stringResource(R.string.onboarding_done_place_skipped))
    }
    return rows + listOf(
        stringResource(PlaceKind.HOME.labelRes) to placeSummary(state.home, home),
        stringResource(PlaceKind.OFFICE.labelRes) to placeSummary(state.office, office),
    )
}

// "지금 여기"로 정한 곳은 주소 대신 그렇게 정했다고 보여 준다
@Composable
private fun placeSummary(row: PlaceRow, place: TripPlace): String =
    if (row.mode == PlaceMode.HERE) stringResource(R.string.onboarding_place_here_text) else place.displayName
