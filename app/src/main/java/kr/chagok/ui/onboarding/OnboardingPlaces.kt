package kr.chagok.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.ui.components.Hint
import kr.chagok.ui.components.pressable
import kr.chagok.ui.components.selectedFrame
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

private val PLACE_ICON = 40.dp
private val OPTION_HEIGHT = 64.dp
private const val OPTION_DISABLED_ALPHA = 0.35f

/** 집·회사 (선택): 내비 즐겨찾기가 맞는지, 지금 여기인지, 안 쓸지 */
@Composable
fun PlacesStep(state: OnboardingUiState, isWide: Boolean, onSelect: (PlaceKind, PlaceMode) -> Unit) {
    StepHeading(
        title = stringResource(R.string.onboarding_places_title),
        sub = stringResource(R.string.onboarding_places_sub),
        isWide = isWide,
        optional = stringResource(R.string.onboarding_optional),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        PlaceCard(state.home, state.isHereFailed, isWide, onSelect)
        PlaceCard(state.office, state.isHereFailed, isWide, onSelect)
    }
    if (state.isHereUsed) Hint(stringResource(R.string.onboarding_place_here_one))
    if (state.isHereUsed && state.isHereFailed) {
        Hint(stringResource(R.string.onboarding_place_here_failed), isWarning = true)
    } else if (state.isOnePlaceOnly && !state.isHereLoading) {
        // 한 곳만 정하면 정리 화면에서 둘 다 말없이 빠지므로 여기서 미리 알린다.
        // 위치를 읽는 동안은 "지금 여기" 자리가 잠깐 비어 한 곳만 정한 것처럼 보이니 알리지 않는다
        Hint(stringResource(R.string.onboarding_place_need_both), isWarning = true)
    }
}

// 분할 칸에서는 버튼 3개를 아랫줄에, 넓게에서는 오른쪽 끝에
@Composable
private fun PlaceCard(row: PlaceRow, isHereFailed: Boolean, isWide: Boolean, onSelect: (PlaceKind, PlaceMode) -> Unit) {
    val cardModifier = Modifier
        .fillMaxWidth()
        .background(ChagokColors.card, Shapes.card)
        .padding(Spacing.l)
    if (isWide) {
        Row(
            cardModifier,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaceLabel(row, isHereFailed, Modifier.weight(1f))
            PlaceOptions(row, onSelect, isEqualWidth = false)
        }
    } else {
        Column(cardModifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            PlaceLabel(row, isHereFailed, Modifier.fillMaxWidth())
            PlaceOptions(row, onSelect, isEqualWidth = true)
        }
    }
}

@Composable
private fun PlaceLabel(row: PlaceRow, isHereFailed: Boolean, modifier: Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (row.kind == PlaceKind.HOME) Icons.Rounded.Home else Icons.Rounded.Apartment,
            contentDescription = null,
            tint = ChagokColors.accent,
            modifier = Modifier.size(PLACE_ICON),
        )
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(row.kind.labelRes), fontSize = TextSizes.subtitle, fontWeight = FontWeight.Bold)
                // 어디서 온 값인지는 칩 대신 흐린 글자로 보인다
                if (row.mode == PlaceMode.BOOKMARK) {
                    Text(
                        stringResource(R.string.onboarding_bookmark),
                        Modifier.padding(start = Spacing.s),
                        color = ChagokColors.textSub,
                    )
                }
            }
            Text(placeText(row, isHereFailed), color = ChagokColors.textSub)
        }
    }
}

// 지금 고른 방식으로 정해질 곳. 즐겨찾기가 없어 "안 쓸래요"가 된 쪽은 왜 그런지 알려 준다
@Composable
private fun placeText(row: PlaceRow, isHereFailed: Boolean): String = when (row.mode) {
    PlaceMode.BOOKMARK -> row.bookmark?.displayName.orEmpty()
    PlaceMode.HERE -> stringResource(
        if (isHereFailed) R.string.onboarding_place_here_failed_short else R.string.onboarding_place_here_text,
    )
    PlaceMode.SKIP -> stringResource(
        if (row.hasBookmark) R.string.onboarding_place_skipped else R.string.onboarding_place_no_bookmark,
    )
}

@Composable
private fun PlaceOptions(row: PlaceRow, onSelect: (PlaceKind, PlaceMode) -> Unit, isEqualWidth: Boolean) {
    Row(
        modifier = if (isEqualWidth) Modifier.fillMaxWidth() else Modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        PlaceMode.entries.forEach { mode ->
            val isEnabled = row.canPick(mode)
            val isOn = row.mode == mode
            Box(
                modifier = Modifier
                    .then(if (isEqualWidth) Modifier.weight(1f) else Modifier)
                    .height(OPTION_HEIGHT)
                    .alpha(if (isEnabled) 1f else OPTION_DISABLED_ALPHA)
                    .selectedFrame(isOn, Shapes.control, idleBackground = ChagokColors.cardStrong)
                    .semantics { selected = isOn }
                    .pressable(enabled = isEnabled, role = Role.RadioButton) { onSelect(row.kind, mode) }
                    .padding(horizontal = Spacing.l),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(mode.labelRes), fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@get:StringRes
private val PlaceMode.labelRes: Int
    get() = when (this) {
        PlaceMode.BOOKMARK -> R.string.onboarding_place_ok
        PlaceMode.HERE -> R.string.onboarding_place_here
        PlaceMode.SKIP -> R.string.onboarding_place_skip
    }
