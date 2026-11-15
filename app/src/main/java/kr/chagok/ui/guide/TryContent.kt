package kr.chagok.ui.guide

import androidx.annotation.StringRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.domain.model.Category
import kr.chagok.ui.components.DepartureToggle
import kr.chagok.ui.components.DepartureToggleSize
import kr.chagok.ui.components.GleoExampleTicker
import kr.chagok.ui.components.Hint
import kr.chagok.ui.components.departureLabelRes
import kr.chagok.ui.theme.IconSizes
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

private val BOX_PADDING = Spacing.xl
private const val SAY_BOX_WEIGHT = 1.15f // 넓게에서 말할 문장 쪽을 조금 넓게
private val DONE_ICON = 38.dp
private val PULSE_SIZE = 46.dp
private const val PULSE_MILLIS = 800
private const val PULSE_SCALE = 1.12f
private const val PULSE_ALPHA = 0.22f

/**
 * "Gleo에게 말해 보세요"(Gleo 가 없는 차는 "1탭으로 바꿔 보기") 본문. 첫 실행 단계와 홈 시작 가이드 시트가 같이 쓴다.
 *
 * @param defaultCategory 기본 분류 (토글은 고른 값이 없으면 이 값)
 * @param revertNote 연습 값을 언제 되돌리는지 ("시작하기를 누르면" / "닫으면")
 */
@Composable
fun TryContent(
    practice: PracticeState?,
    defaultCategory: Category,
    hasGleo: Boolean,
    isShared: Boolean,
    isWide: Boolean,
    revertNote: String,
    onTap: (Category) -> Unit,
) {
    val target = practice?.target ?: ChangePractice.targetFor(defaultCategory)
    val isDone = practice?.isDone == true
    val sayBox = @Composable { modifier: Modifier -> SayBox(target, hasGleo, isShared, modifier) }
    val liveBox = @Composable { modifier: Modifier ->
        LiveBox(practice?.picked ?: defaultCategory, target, isDone, hasGleo, revertNote, onTap, modifier)
    }
    if (isWide) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            sayBox(Modifier.weight(SAY_BOX_WEIGHT))
            liveBox(Modifier.weight(1f))
        }
    } else {
        sayBox(Modifier.fillMaxWidth())
        liveBox(Modifier.fillMaxWidth())
    }
}

@Composable
private fun SayBox(target: Category, hasGleo: Boolean, isShared: Boolean, modifier: Modifier) {
    Panel(modifier) {
        if (hasGleo) {
            Instruction(Icons.Rounded.RecordVoiceOver, stringResource(R.string.try_how_call))
            Text(stringResource(target.phraseRes), fontSize = TextSizes.title, fontWeight = FontWeight.ExtraBold)
            Hint(stringResource(R.string.gleo_example_tip_short))
            GleoExampleTicker(isShared)
        } else {
            Instruction(
                Icons.Rounded.TouchApp,
                stringResource(R.string.try_tap_guide, stringResource(target.departureLabelRes)),
            )
            Text(stringResource(R.string.try_tap_text), color = ChagokColors.textSub)
        }
    }
}

@Composable
private fun LiveBox(
    shown: Category,
    target: Category,
    isDone: Boolean,
    hasGleo: Boolean,
    revertNote: String,
    onTap: (Category) -> Unit,
    modifier: Modifier,
) {
    Panel(modifier) {
        Text(stringResource(R.string.next_label), color = ChagokColors.textSub)
        // Gleo 연습에서는 보여 주기만 하고(말로 바뀌는 걸 본다), 1탭 연습에서는 누른다
        DepartureToggle(shown, onPick = if (hasGleo) null else onTap, size = DepartureToggleSize.Compact)
        if (isDone) {
            Text(
                stringResource(R.string.next_changed_note, stringResource(target.departureLabelRes)),
                color = ChagokColors.pendingText,
                fontWeight = FontWeight.Bold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = ChagokColors.green,
                    modifier = Modifier.size(DONE_ICON),
                )
                Text(
                    stringResource(R.string.try_done),
                    color = ChagokColors.green,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = TextSizes.subtitle,
                )
            }
            Text(revertNote, color = ChagokColors.textDim)
        } else {
            Waiting(
                stringResource(if (hasGleo) R.string.try_waiting_gleo else R.string.try_waiting_tap),
                if (hasGleo) Icons.Rounded.Mic else Icons.Rounded.TouchApp,
            )
        }
    }
}

@Composable
private fun Panel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.background(ChagokColors.card, Shapes.card).padding(BOX_PADDING),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        content = content,
    )
}

@Composable
private fun Instruction(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = ChagokColors.accent, modifier = Modifier.size(IconSizes.small))
        Text(text, color = ChagokColors.textSub)
    }
}

@Composable
private fun Waiting(text: String, icon: ImageVector) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        1f,
        PULSE_SCALE,
        infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse),
        label = "scale",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(PULSE_SIZE)
                .scale(scale)
                .background(ChagokColors.gleo.copy(alpha = PULSE_ALPHA), Shapes.pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ChagokColors.gleo)
        }
        Text(text, color = ChagokColors.textSub)
    }
}

/** 연습으로 말해 볼 문장. Gleo 가 알아듣는 것을 확인한 말로 골랐다 */
@get:StringRes
internal val Category.phraseRes: Int
    get() = if (this == Category.PERSONAL) R.string.try_phrase_personal else R.string.try_phrase_business
