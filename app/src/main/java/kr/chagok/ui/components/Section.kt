package kr.chagok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

private val RULED_CELL_H = Spacing.m
private val RULED_CELL_V = Spacing.m

/** 탭 화면 바깥 여백. 기록부처럼 LazyColumn 으로 그리는 화면도 같이 쓴다 */
val ScreenPadding = PaddingValues(
    start = Spacing.screenHorizontal,
    end = Spacing.screenHorizontal,
    top = Spacing.screenTop,
    bottom = Spacing.screenBottom,
)

/** 넓은 창 탭 화면 바깥 여백. 칸이 넓어서 가장자리와 더 띄운다 */
val WideScreenPadding = PaddingValues(
    start = Spacing.wideHorizontal,
    end = Spacing.wideHorizontal,
    top = Spacing.wideTop,
    bottom = Spacing.wideBottom,
)

/** 탭 화면 틀: 세로로 넘기고, 덩어리 사이를 같은 간격으로 띄운다 */
@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = ScreenPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
        content = content,
    )
}

/**
 * 섹션: 상자 없이 화면 바탕 위에 둔다. 상자(바탕색 있는 둥근 모양)는 누르는 것과 방금 운행 카드에만 쓴다.
 * 정보 묶음까지 상자로 감싸면 상자 안에 상자가 생긴다.
 */
@Composable
fun Section(
    modifier: Modifier = Modifier,
    gap: Dp = Spacing.s,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sectionVertical),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/**
 * 섹션 제목. [count] 가 있으면 할 일 개수 배지(주황), [trailing] 이 있으면 오른쪽 끝에 흐린 글자.
 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, count: Int? = null, trailing: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = TextSizes.subtitle, fontWeight = FontWeight.SemiBold)
        if (count != null) {
            Text(
                text = count.toString(),
                modifier = Modifier
                    .background(ChagokColors.pending, Shapes.pill)
                    .padding(horizontal = Spacing.s),
                color = ChagokColors.onPending,
                fontSize = TextSizes.body,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(trailing, color = ChagokColors.textSub, fontSize = TextSizes.body)
        }
    }
}

/** 위아래 선 사이 한 줄: 정보 칸 여러 개를 상자 없이 묶는다. 칸 사이는 [RuledDivider] */
@Composable
fun RuledRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = ChagokColors.divider)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), content = content)
        HorizontalDivider(color = ChagokColors.divider)
    }
}

/** [RuledRow] 칸 사이 세로선 */
@Composable
fun RuledDivider() {
    VerticalDivider(color = ChagokColors.divider)
}

/** [RuledRow] 칸 안쪽 여백. 첫 칸은 왼쪽 선이 없어서 제목과 줄을 맞추려고 왼쪽 여백을 뺀다 */
fun Modifier.ruledCell(isFirst: Boolean): Modifier =
    padding(start = if (isFirst) 0.dp else RULED_CELL_H, end = RULED_CELL_H, top = RULED_CELL_V, bottom = RULED_CELL_V)
