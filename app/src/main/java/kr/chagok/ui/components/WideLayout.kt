package kr.chagok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes
import kr.chagok.ui.theme.TouchSizes

/** 넓은 창 왼쪽 칸. 누르는 것이 운전석 쪽에서 크게 눌리는 너비 */
val WideSideWidth = 420.dp

/** 넓은 창에서 화면을 끌고 가는 숫자 하나 */
val HeroNumberSize = 120.sp
private const val HERO_LINE_HEIGHT = 1.05f
private const val HERO_UNIT_RATIO = 0.36f

private val DOT_SIZE = 12.dp

/**
 * 넓은 창 두 줄 배치. 위 줄은 누르는 것 | 주인공 숫자, 아래 줄은 곁 정보 | 그림·목록.
 * 빈 여백으로 띄우지 않고 좌우 줄을 맞춘다. 아래 왼쪽은 길어지면 넘겨 보고, 아래 오른쪽은 남은 높이를 채운다.
 */
@Composable
fun WideGrid(
    topSide: @Composable ColumnScope.() -> Unit,
    topMain: @Composable ColumnScope.() -> Unit,
    bottomSide: @Composable ColumnScope.() -> Unit,
    bottomMain: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(WideScreenPadding), verticalArrangement = Arrangement.spacedBy(Spacing.wideRowGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.wideColumnGap)) {
            Column(Modifier.width(WideSideWidth), content = topSide)
            Column(Modifier.weight(1f), content = topMain)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.wideColumnGap)) {
            Column(
                Modifier.width(WideSideWidth).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.wideBlockGap),
                content = bottomSide,
            )
            Column(Modifier.weight(1f).fillMaxHeight(), content = bottomMain)
        }
    }
}

/** 아래 줄 칸 머리: 제목과 (있으면) 오른쪽 끝 버튼. 좌우 칸 머리 높이를 버튼 높이로 맞춘다 */
@Composable
fun WideHead(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().heightIn(min = TouchSizes.headButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), fontSize = TextSizes.subtitle, fontWeight = FontWeight.SemiBold)
        trailing?.invoke()
    }
}

/** 이름표 + 큰 숫자(작은 단위) + 흐린 한 줄. 화면마다 하나만 둔다 */
@Composable
fun HeroNumber(
    label: String,
    number: String,
    unit: String,
    modifier: Modifier = Modifier,
    numberModifier: Modifier = Modifier,
    fontSize: TextUnit = HeroNumberSize,
    line: @Composable (() -> Unit)? = null,
) {
    Column(modifier) {
        Text(label, color = ChagokColors.textSub)
        Row(Modifier.padding(top = Spacing.xxs), verticalAlignment = Alignment.Bottom) {
            Text(
                number,
                modifier = numberModifier,
                fontSize = fontSize,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = fontSize * HERO_LINE_HEIGHT,
                maxLines = 1,
            )
            Text(
                unit,
                modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.m),
                color = ChagokColors.textSub,
                fontSize = fontSize * HERO_UNIT_RATIO,
                fontWeight = FontWeight.Bold,
            )
        }
        if (line != null) Box(Modifier.padding(top = Spacing.m)) { line() }
    }
}

/**
 * 선 하나로만 나눈 목록 한 줄: 분류 점 · (날짜) · 이름 · (흐린 덧말) · 값.
 * 홈 오늘 운행, 기록부 분류별·최근 운행이 같이 쓴다.
 */
@Composable
fun DotRow(
    color: Color,
    text: String,
    value: String,
    modifier: Modifier = Modifier,
    leading: String? = null,
    note: String? = null,
    isFirst: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        if (!isFirst) HorizontalDivider(color = ChagokColors.divider)
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
                .padding(vertical = Spacing.l),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(DOT_SIZE).background(color, Shapes.block))
            if (leading != null) Text(leading, color = ChagokColors.textSub, maxLines = 1)
            Text(
                text,
                if (note == null) Modifier.weight(1f) else Modifier,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (note != null) Text(note, Modifier.weight(1f), color = ChagokColors.textSub, maxLines = 1)
            Text(value, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
