package kr.chagok.ui.guide

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.ui.components.HeaderButton
import kr.chagok.ui.components.pressable
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

private val PROGRESS_HEIGHT = 6.dp
private val CHECK_ICON = 32.dp

/**
 * 홈 시작 가이드 카드: 할 일과 진행률. 안 한 일에는 바로 하는 버튼(PIN 정하기 · 설정 열기 · 해 보기).
 * 닫기 버튼으로 닫으면 다시 안 보인다. 다 하면 저절로 사라진다 ([StartGuide.of]).
 */
@Composable
fun StartGuideCard(guide: StartGuide, onAction: (GuideItem) -> Unit, onDismiss: () -> Unit) {
    val progress by animateFloatAsState(guide.doneCount.toFloat() / guide.rows.size, label = "guide")
    Column(
        Modifier
            .fillMaxWidth()
            .background(ChagokColors.card, Shapes.card)
            .padding(horizontal = Spacing.xl, vertical = Spacing.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.guide_title), fontSize = TextSizes.subtitle, fontWeight = FontWeight.Bold)
            Text("${guide.doneCount}/${guide.rows.size}", color = ChagokColors.accent, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.guide_close),
                tint = ChagokColors.textDim,
                modifier = Modifier.pressable(onClick = onDismiss).padding(Spacing.xs),
            )
        }
        Box(
            Modifier
                .padding(top = Spacing.s, bottom = Spacing.xs)
                .fillMaxWidth()
                .height(PROGRESS_HEIGHT)
                .background(ChagokColors.cardStrong, Shapes.pill),
        ) {
            Box(Modifier.fillMaxWidth(progress).height(PROGRESS_HEIGHT).background(ChagokColors.accent, Shapes.pill))
        }
        guide.rows.forEach { row ->
            HorizontalDivider(color = ChagokColors.divider)
            GuideRowLine(row, guide.hasGleo, onAction)
        }
    }
}

@Composable
private fun GuideRowLine(row: GuideRow, hasGleo: Boolean, onAction: (GuideItem) -> Unit) {
    val texts = row.item.texts(hasGleo)
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (row.isDone) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (row.isDone) ChagokColors.green else ChagokColors.textDim,
            modifier = Modifier.size(CHECK_ICON),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                stringResource(texts.title),
                fontWeight = FontWeight.Bold,
                color = if (row.isDone) ChagokColors.textSub else ChagokColors.text,
                textDecoration = if (row.isDone) TextDecoration.LineThrough else null,
            )
            Text(stringResource(texts.sub), color = ChagokColors.textDim)
        }
        val action = texts.action
        if (!row.isDone && action != null) {
            HeaderButton(onClick = { onAction(row.item) }, icon = texts.icon, text = stringResource(action))
        }
    }
}

private class GuideTexts(
    @StringRes val title: Int,
    @StringRes val sub: Int,
    @StringRes val action: Int?,
    val icon: ImageVector,
)

private fun GuideItem.texts(hasGleo: Boolean): GuideTexts = when (this) {
    GuideItem.RECORDING -> GuideTexts(
        R.string.guide_recording,
        R.string.guide_recording_sub,
        null,
        Icons.Rounded.CheckCircle,
    )
    GuideItem.ADMIN_PIN -> GuideTexts(
        R.string.guide_pin,
        R.string.guide_pin_sub,
        R.string.guide_pin_action,
        Icons.Rounded.Pin,
    )
    GuideItem.PLACES -> GuideTexts(
        R.string.guide_places,
        R.string.guide_places_sub,
        R.string.guide_places_action,
        Icons.Rounded.Settings,
    )
    GuideItem.CHANGE -> if (hasGleo) {
        GuideTexts(R.string.guide_gleo, R.string.guide_gleo_sub, R.string.guide_try_action, Icons.Rounded.Mic)
    } else {
        GuideTexts(R.string.guide_tap, R.string.guide_tap_sub, R.string.guide_try_action, Icons.Rounded.TouchApp)
    }
    GuideItem.FIRST_TRIP -> GuideTexts(R.string.guide_trip, R.string.guide_trip_sub, null, Icons.Rounded.CheckCircle)
}
