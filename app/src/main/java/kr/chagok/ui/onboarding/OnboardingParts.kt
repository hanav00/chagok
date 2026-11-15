package kr.chagok.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kr.chagok.ui.components.pressable
import kr.chagok.ui.components.selectedFrame
import kr.chagok.ui.theme.IconSizes
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

private val BUTTON_HEIGHT = 76.dp
private val BUTTON_PADDING = Spacing.xxl
private val TEXT_BUTTON_PADDING = Spacing.s
private val PRIMARY_MIN_WIDTH = 200.dp
private const val DISABLED_ALPHA = 0.4f
private val CHOICE_GAP = Spacing.s
private const val BADGE_TINT_ALPHA = 0.14f
private val CHOICE_ICON = 48.dp
private val CHOICE_ICON_WIDE = 56.dp
private val CHOICE_MIN_HEIGHT_WIDE = 180.dp
private val PROMISE_ICON = 24.dp
private const val SUB_LINE_HEIGHT = 1.5f
private const val TITLE_LINE_HEIGHT = 1.3f

/** 주 버튼 · 카드색 버튼 · 바탕 없이 글자만 있는 버튼 */
enum class OnboardingButtonStyle { Primary, Ghost, Plain }

/** 첫 실행 버튼. 주 버튼은 흐리게만 하면 강조색이 남아 눌릴 것처럼 보여서, 막히면 회색 버튼이 된다 */
@Composable
fun OnboardingButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: OnboardingButtonStyle = OnboardingButtonStyle.Primary,
    iconBefore: ImageVector? = null,
    iconAfter: ImageVector? = null,
    enabled: Boolean = true,
) {
    val isPrimary = style == OnboardingButtonStyle.Primary
    val background = when {
        isPrimary && enabled -> ChagokColors.accent
        style == OnboardingButtonStyle.Plain -> null
        else -> ChagokColors.card
    }
    val content = when {
        isPrimary -> if (enabled) ChagokColors.onAccent else ChagokColors.textDim
        style == OnboardingButtonStyle.Plain -> ChagokColors.textSub
        else -> ChagokColors.text
    }
    Row(
        modifier = modifier
            .height(BUTTON_HEIGHT)
            .then(if (isPrimary) Modifier.defaultMinSize(minWidth = PRIMARY_MIN_WIDTH) else Modifier)
            .alpha(if (enabled || isPrimary) 1f else DISABLED_ALPHA)
            .then(if (background != null) Modifier.background(background, Shapes.control) else Modifier)
            .pressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (style == OnboardingButtonStyle.Plain) TEXT_BUTTON_PADDING else BUTTON_PADDING),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconBefore != null) {
            Icon(iconBefore, contentDescription = null, tint = content, modifier = Modifier.size(IconSizes.small))
        }
        Text(text, color = content, fontSize = TextSizes.subtitle, fontWeight = FontWeight.Bold, maxLines = 1)
        if (iconAfter != null) {
            Icon(iconAfter, contentDescription = null, tint = content, modifier = Modifier.size(IconSizes.small))
        }
    }
}

/** 단계 제목 + 설명 한 줄. [optional] 이 있으면 제목 뒤에 작게 "(선택)" */
@Composable
fun StepHeading(title: String, sub: String, isWide: Boolean, optional: String? = null) {
    // 제목과 설명 줄은 단계 본문 사이 간격보다 가깝게 붙인다
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        val titleSize = if (isWide) TextSizes.display else TextSizes.headline
        Text(
            text = buildAnnotatedString {
                append(title)
                if (optional != null) {
                    append(" ")
                    withStyle(
                        SpanStyle(
                            color = ChagokColors.textSub,
                            fontSize = TextSizes.subtitle,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    ) {
                        append(optional)
                    }
                }
            },
            fontSize = titleSize,
            fontWeight = FontWeight.ExtraBold,
            style = LocalTextStyle.current.copy(lineHeight = titleSize * TITLE_LINE_HEIGHT),
        )
        val subSize = if (isWide) TextSizes.subtitle else TextSizes.body
        Text(
            sub,
            color = ChagokColors.textSub,
            fontSize = subSize,
            style = LocalTextStyle.current.copy(lineHeight = subSize * SUB_LINE_HEIGHT),
        )
    }
}

/** 고를 수 있는 것 하나 (누가 쓰나요 · 기본 분류) */
data class ChoiceOption<T>(val value: T, val icon: ImageVector, val title: String, val badge: String? = null)

/** 선택지 카드 묶음. 분할 칸에서는 세로로 쌓고, 넓게에서는 가로로 늘어놓는다 */
@Composable
fun <T> ChoiceCards(options: List<ChoiceOption<T>>, selected: T?, onSelect: (T) -> Unit, isWide: Boolean) {
    if (!isWide) {
        Column(verticalArrangement = Arrangement.spacedBy(CHOICE_GAP)) {
            options.forEach {
                ChoiceCard(it, it.value == selected, isWide = false, Modifier.fillMaxWidth()) { onSelect(it.value) }
            }
        }
        return
    }
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(CHOICE_GAP)) {
        options.forEach {
            ChoiceCard(
                it,
                it.value == selected,
                isWide = true,
                Modifier.weight(1f).fillMaxHeight(),
            ) { onSelect(it.value) }
        }
    }
}

@Composable
private fun <T> ChoiceCard(
    option: ChoiceOption<T>,
    isSelected: Boolean,
    isWide: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val cardModifier = modifier
        .then(if (isWide) Modifier.defaultMinSize(minHeight = CHOICE_MIN_HEIGHT_WIDE) else Modifier)
        .selectedFrame(isSelected, Shapes.card, idleBackground = ChagokColors.card)
        .semantics { selected = isSelected }
        .pressable(role = Role.RadioButton, onClick = onClick)
    val icon = @Composable {
        Icon(
            option.icon,
            contentDescription = null,
            tint = ChagokColors.accent,
            modifier = Modifier.size(if (isWide) CHOICE_ICON_WIDE else CHOICE_ICON),
        )
    }
    val title = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(option.title, fontSize = TextSizes.title, fontWeight = FontWeight.Bold)
            option.badge?.let { Badge(it, Modifier.padding(start = Spacing.s)) }
        }
    }
    if (isWide) {
        Column(cardModifier.padding(Spacing.xxl), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            icon()
            title()
        }
    } else {
        Row(
            cardModifier.padding(horizontal = Spacing.xl, vertical = Spacing.l),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            title()
        }
    }
}

/** 선택지 이름 옆 작은 알약 배지 ("추천") */
@Composable
private fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .background(ChagokColors.accent.copy(alpha = BADGE_TINT_ALPHA), Shapes.pill)
            .padding(horizontal = Spacing.s, vertical = Spacing.xxs),
        color = ChagokColors.accent,
        fontSize = TextSizes.caption,
        fontWeight = FontWeight.SemiBold,
    )
}

/** 약속·역할 한 줄 ([isOff] 면 아이콘을 흐리게) */
data class PromiseLine(val icon: ImageVector, val title: String, val text: String, val isOff: Boolean = false)

/** 카드 없이 글로만 두는 줄 목록. 선택지와 헷갈리지 않게 위에 선 하나만 */
@Composable
fun PromiseList(lines: List<PromiseLine>) {
    Column {
        Spacer(Modifier.height(Spacing.xxs))
        HorizontalDivider(color = ChagokColors.divider)
        Column(
            Modifier.padding(start = Spacing.xxs, end = Spacing.xxs, top = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            lines.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Icon(
                        imageVector = line.icon,
                        contentDescription = null,
                        tint = if (line.isOff) ChagokColors.textDim else ChagokColors.green,
                        modifier = Modifier.size(PROMISE_ICON),
                    )
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = ChagokColors.text, fontWeight = FontWeight.Bold)) {
                                append(line.title)
                            }
                            append(" ")
                            append(line.text)
                        },
                        color = ChagokColors.textSub,
                        style = LocalTextStyle.current.copy(lineHeight = TextSizes.body * SUB_LINE_HEIGHT),
                    )
                }
            }
        }
    }
}
