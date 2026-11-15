package kr.chagok.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.chagok.R
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.DistanceTotals
import kr.chagok.ui.components.MutedText
import kr.chagok.ui.components.SectionTitle
import kr.chagok.ui.components.WarningLine
import kr.chagok.ui.components.color
import kr.chagok.ui.components.labelRes
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

/** 업무사용비율 큰 숫자: 요약 카드의 주인공 */
private val RATIO_FONT_SIZE = 64.sp
private const val RATIO_LINE_HEIGHT = 1.05f

private const val RATIO_COUNT_MILLIS = 1_800

private val LEGEND_DOT_SIZE = 12.dp

/** 이번 달 범례 순서. null 은 분류 대기 */
private val MONTH_ORDER: List<Category?> = listOf(Category.COMMUTE, Category.BUSINESS, Category.PERSONAL, null)

/**
 * 볼거리 묶음: 사업연도 업무사용비율 · 이번 달 운행 · 기록 연속성. 섹션처럼 상자 없이 둔다.
 * [isIntro] 면 비율 숫자가 0 에서 올라가고 이번 달 블록이 날짜순으로 떨어져 쌓인다.
 */
@Composable
internal fun SummaryCard(summary: SummaryState, isIntro: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sectionVertical),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        RatioHero(summary.fiscalYear, summary.yearTotals, isIntro)
        MonthBlock(summary, isIntro)
        IntegrityLine(summary.odometerKm, summary.unrecordedKm)
    }
}

/** 숫자 하나가 주인공. 그림은 바로 아래 이번 달 블록이 맡는다 */
@Composable
internal fun RatioHero(
    fiscalYear: Int,
    totals: DistanceTotals,
    isIntro: Boolean,
    fontSize: TextUnit = RATIO_FONT_SIZE,
    isFactsBelow: Boolean = false,
) {
    val ratio = totals.businessRatio.toFloat()
    val shown = remember { Animatable(if (isIntro) 0f else ratio) }
    var hasCounted by remember { mutableStateOf(!isIntro) }
    // 처음 한 번만 올라가고, 그 뒤 운행이 바뀌면 바로 바뀐다
    LaunchedEffect(ratio) {
        if (hasCounted) {
            shown.snapTo(ratio)
        } else {
            shown.animateTo(ratio, tween(RATIO_COUNT_MILLIS))
            hasCounted = true
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.home_ratio_title, fiscalYear), color = ChagokColors.textSub)
            if (totals.total > 0) {
                Text(
                    text = Formatters.percent(shown.value.toDouble()),
                    modifier = Modifier.padding(top = Spacing.xxs),
                    // 숫자는 흰색. 업무 색은 아래 막대·점에만 쓴다
                    color = ChagokColors.text,
                    fontSize = fontSize,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = fontSize * RATIO_LINE_HEIGHT,
                )
            } else {
                // 달린 거리가 없을 때 큰 "0.0%"는 "이 차는 업무에 안 쓴다"로 읽힌다
                Text(
                    text = stringResource(R.string.home_ratio_empty),
                    modifier = Modifier.padding(top = Spacing.xs),
                    fontSize = TextSizes.subtitle,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (isFactsBelow && totals.total > 0) HeroFactsLine(totals)
        }
        if (!isFactsBelow) {
            Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                HeroFact(stringResource(R.string.home_business_km), totals.businessUseKm)
                HeroFact(stringResource(R.string.home_total_km), totals.total)
            }
        }
    }
}

/** 넓은 창: 업무용 · 전체 거리를 숫자 아래 한 줄로 */
@Composable
private fun HeroFactsLine(totals: DistanceTotals) {
    Row(Modifier.padding(top = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.home_business_km), color = ChagokColors.textSub)
        Text(stringResource(R.string.km_value_spaced, Formatters.km(totals.businessUseKm, digits = 0)), fontWeight = FontWeight.Bold)
        Text(Formatters.SEPARATOR.trim(), color = ChagokColors.textSub)
        Text(stringResource(R.string.home_total_km), color = ChagokColors.textSub)
        Text(stringResource(R.string.km_value_spaced, Formatters.km(totals.total, digits = 0)), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HeroFact(label: String, km: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(label, color = ChagokColors.textSub, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.km_value, Formatters.km(km)), fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthBlock(summary: SummaryState, isIntro: Boolean) {
    val totals = summary.monthTotals
    val parts = MONTH_ORDER.map { it to totals.kmOf(it) }.filter { (_, km) -> km > 0 }
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = ChagokColors.divider)
        SectionTitle(
            text = stringResource(R.string.home_month_title, Formatters.month(summary.month)),
            modifier = Modifier.padding(top = Spacing.l, bottom = Spacing.m),
            trailing = stringResource(R.string.km_value, Formatters.km(totals.total)),
        )
        MonthBlocks(summary.monthDays, summary.todayOfMonth, isIntro)
        FlowRow(
            modifier = Modifier.padding(top = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            parts.forEach { (category, km) -> LegendItem(category, km) }
        }
    }
}

@Composable
internal fun LegendItem(category: Category?, km: Double) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(LEGEND_DOT_SIZE).background(category.color, Shapes.pill))
        Text(stringResource(category?.labelRes ?: R.string.category_pending), color = ChagokColors.textSub)
        Text(stringResource(R.string.km_value, Formatters.km(km)), fontWeight = FontWeight.Bold)
    }
}

/**
 * 계기판 누적거리와 마지막 기록이 이어지는지. 빠졌을 때만 경고하고,
 * 평소엔 "괜찮아요" 대신 어디까지 기록됐는지 사실만 흐린 한 줄로 보인다.
 */
@Composable
internal fun IntegrityLine(odometerKm: Double?, unrecordedKm: Double) {
    if (odometerKm == null) return
    if (unrecordedKm > 0) {
        WarningLine(stringResource(R.string.home_integrity_gap, Formatters.km(unrecordedKm)))
    } else {
        MutedText(stringResource(R.string.home_integrity_ok, Formatters.km(odometerKm)))
    }
}
