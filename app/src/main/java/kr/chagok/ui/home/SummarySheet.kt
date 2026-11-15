package kr.chagok.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.chagok.R
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.DistanceTotals
import kr.chagok.ui.components.FactCell
import kr.chagok.ui.components.SectionTitle
import kr.chagok.ui.components.SheetOverlay
import kr.chagok.ui.components.TripBlock
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

private val SPLIT_BAR_HEIGHT = 20.dp

/** 분류 막대·범례 순서. null 은 분류 대기 */
private val SPLIT_ORDER: List<Category?> = listOf(Category.COMMUTE, Category.BUSINESS, Category.PERSONAL, null)

/** 홈 "자세히": 올해·이번 달을 분류별로 나눈 거리와 계기판 이어짐. 홈과 같은 상태를 읽는다 */
@Composable
internal fun SummarySheet(onDismiss: () -> Unit, viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary = state?.summary ?: return
    SheetOverlay(
        onDismiss = onDismiss,
        header = {
            Text(stringResource(R.string.home_detail_title), fontSize = TextSizes.title, fontWeight = FontWeight.Bold)
        },
    ) {
        SectionTitle(
            stringResource(R.string.home_ratio_title, summary.fiscalYear),
            Modifier.padding(top = Spacing.m),
            trailing = Formatters.percent(summary.yearTotals.businessRatio),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxl)) {
            FactCell(R.string.home_business_km, stringResource(R.string.km_value, Formatters.km(summary.yearTotals.businessUseKm)))
            FactCell(R.string.home_total_km, stringResource(R.string.km_value, Formatters.km(summary.yearTotals.total)))
        }
        SplitBar(summary.yearTotals)
        SectionTitle(
            stringResource(R.string.home_month_title, Formatters.month(summary.month)),
            Modifier.padding(top = Spacing.xl),
            trailing = stringResource(R.string.km_value, Formatters.km(summary.monthTotals.total)),
        )
        SplitBar(summary.monthTotals)
        Column(Modifier.padding(top = Spacing.l)) {
            IntegrityLine(summary.odometerKm, summary.unrecordedKm)
        }
    }
}

/** 분류별 거리를 블록 몇 개로 이은 막대(폭 = 거리)와 범례 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitBar(totals: DistanceTotals) {
    val parts = SPLIT_ORDER.map { it to totals.kmOf(it) }.filter { (_, km) -> km > 0 }
    if (parts.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().height(SPLIT_BAR_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        parts.forEach { (category, km) ->
            TripBlock(category, isGap = false, modifier = Modifier.weight(km.toFloat()).fillMaxHeight())
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        parts.forEach { (category, km) -> LegendItem(category, km) }
    }
}
