package kr.chagok.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.chagok.domain.model.Category
import kr.chagok.ui.components.color
import kr.chagok.ui.components.drivingLabelRes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

private val CATEGORY_BAR_WIDTH = 8.dp
private const val LINE_HEIGHT_RATIO = 1.3f

/**
 * 주행 중 화면 (P단이 아닐 때, 신호 대기 포함): 이번 운행 분류 한 줄만 보인다.
 * 누를 것이 없고, 시선을 빼앗지 않게 늘어나는 거리 같은 바뀌는 숫자와 움직이는 효과도 넣지 않는다.
 * 왼쪽 분류 색 막대 + 큰 글자로 계기판 상태 표시처럼 한눈에 읽힌다.
 */
@Composable
fun DrivingScreen(category: Category) {
    Box(Modifier.fillMaxSize().padding(horizontal = Spacing.xxxl), contentAlignment = Alignment.CenterStart) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            Box(
                Modifier
                    .width(CATEGORY_BAR_WIDTH)
                    .fillMaxHeight()
                    .background(category.color),
            )
            Text(
                text = stringResource(category.drivingLabelRes),
                modifier = Modifier.padding(vertical = Spacing.xs),
                fontSize = TextSizes.display,
                fontWeight = FontWeight.Bold,
                style = LocalTextStyle.current.copy(lineHeight = TextSizes.display * LINE_HEIGHT_RATIO),
            )
        }
    }
}
