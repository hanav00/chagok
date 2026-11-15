package kr.chagok.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.ui.components.MutedText
import kr.chagok.ui.components.TripBlock
import kr.chagok.ui.components.droppingAt
import kr.chagok.ui.components.staggered
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

private val CHART_HEIGHT = 120.dp
private val MIN_BLOCK_HEIGHT = 4.dp
private val BLOCK_GAP = 2.dp
private val DAY_GAP = Spacing.xxs
private val BASE_HEIGHT = 3.dp

/** 들어올 때: 1일부터 날짜순으로, 그날 안에서는 아래부터 떨어져 쌓인다 */
private val INTRO_DROP_DISTANCE = 120.dp
private const val INTRO_DAY_STAGGER_MILLIS = 45
private const val INTRO_BLOCK_STAGGER_MILLIS = 90
private const val INTRO_BLOCK_MILLIS = 460

/** 오늘 숫자와 이만큼 가까운 1일·말일 숫자는 겹치지 않게 뺀다 */
private const val LABEL_CLEARANCE_DAYS = 3

/**
 * 이번 달을 날짜마다 블록 탑으로. 블록 하나가 운행 하나, 높이가 달린 거리다.
 * 탑 높이는 모든 날에 같은 km 배율을 써서, 가장 많이 달린 날이 그림 높이를 채운다.
 * 기록 안 된 주행은 점선 빈 블록이라 계기판이 빈 날이 바로 보인다.
 * [isIntro] 면 블록이 1일부터 차례로 떨어져 쌓인다 (그날 처음 홈을 열 때 한 번).
 * [isStage] 면 받은 높이를 다 채운다 (넓은 창 홈).
 */
@Composable
internal fun MonthBlocks(
    days: List<List<TripBlockState>>,
    todayOfMonth: Int,
    isIntro: Boolean,
    modifier: Modifier = Modifier,
    isStage: Boolean = false,
) {
    val introMillis = introMillisOf(days)
    val clock = remember { Animatable(if (isIntro) 0f else introMillis.toFloat()) }
    LaunchedEffect(Unit) {
        if (isIntro) clock.animateTo(introMillis.toFloat(), tween(introMillis, easing = LinearEasing))
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        BoxWithConstraints(
            if (isStage) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(CHART_HEIGHT),
        ) {
            // 달이 막 바뀐 무대는 비어 보이지 않게, 여기에 쌓인다는 것만 알린다
            if (isStage && days.all { it.isEmpty() }) {
                MutedText(stringResource(R.string.home_month_empty), Modifier.align(Alignment.Center))
            }
            val dpPerKm = dpPerKm(days, maxHeight)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(DAY_GAP)) {
                days.forEachIndexed { day, blocks ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(BLOCK_GAP, Alignment.Bottom),
                    ) {
                        // 위에서부터 그리므로 그날 마지막 운행이 맨 위다
                        blocks.withIndex().reversed().forEach { (order, block) ->
                            val delay = day * INTRO_DAY_STAGGER_MILLIS + order * INTRO_BLOCK_STAGGER_MILLIS
                            TripBlock(
                                category = block.category,
                                isGap = block.isGap,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(MIN_BLOCK_HEIGHT + dpPerKm * block.km.toFloat())
                                    .droppingAt({ staggered(clock.value, delay, INTRO_BLOCK_MILLIS) }, INTRO_DROP_DISTANCE),
                            )
                        }
                    }
                }
            }
        }
        DayAxis(days.size, todayOfMonth)
    }
}

/** 날짜 바닥 눈금과 1일 · 오늘 · 말일 숫자. 오늘이 지나지 않은 날은 눈금을 흐리게 */
@Composable
private fun DayAxis(dayCount: Int, todayOfMonth: Int) {
    val labeled = buildSet {
        add(todayOfMonth)
        if (todayOfMonth - 1 > LABEL_CLEARANCE_DAYS) add(1)
        if (dayCount - todayOfMonth > LABEL_CLEARANCE_DAYS) add(dayCount)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DAY_GAP)) {
        for (day in 1..dayCount) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(BASE_HEIGHT)
                        .background(baseColor(day, todayOfMonth), Shapes.pill),
                )
                if (day in labeled) {
                    val isToday = day == todayOfMonth
                    Text(
                        text = day.toString(),
                        modifier = Modifier.padding(top = Spacing.xxs).wrapContentWidth(unbounded = true),
                        color = if (isToday) ChagokColors.text else ChagokColors.textDim,
                        fontSize = TextSizes.caption,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

// 마지막 날 마지막 블록까지 다 떨어지는 시간
private fun introMillisOf(days: List<List<TripBlockState>>): Int {
    val lastDay = days.indexOfLast { it.isNotEmpty() }.coerceAtLeast(0)
    val mostBlocks = days.maxOfOrNull { it.size } ?: 0
    return lastDay * INTRO_DAY_STAGGER_MILLIS + mostBlocks * INTRO_BLOCK_STAGGER_MILLIS + INTRO_BLOCK_MILLIS
}

private fun baseColor(day: Int, todayOfMonth: Int) = when {
    day == todayOfMonth -> ChagokColors.text
    day < todayOfMonth -> ChagokColors.textDim
    else -> ChagokColors.divider
}

// 가장 높은 날(블록 최소 높이·사이 간격을 빼고 남는 높이 ÷ 그날 km)에 맞춘 배율. 모든 날에 같은 배율을 쓴다
private fun dpPerKm(days: List<List<TripBlockState>>, chartHeight: Dp): Dp = days
    .filter { it.isNotEmpty() }
    .minOfOrNull { blocks ->
        val fixed = MIN_BLOCK_HEIGHT * blocks.size + BLOCK_GAP * (blocks.size - 1)
        val km = blocks.sumOf { it.km }.toFloat()
        if (km > 0f) (chartHeight - fixed).coerceAtLeast(0.dp) / km else Dp.Infinity
    }
    ?.takeIf { it != Dp.Infinity }
    ?: 0.dp
