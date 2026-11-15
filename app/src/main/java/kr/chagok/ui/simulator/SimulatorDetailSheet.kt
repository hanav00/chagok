package kr.chagok.ui.simulator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.chagok.R
import kr.chagok.ui.components.FactCell
import kr.chagok.ui.components.SheetOverlay
import kr.chagok.ui.components.SourceText
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

/** 넓은 창 절세 계산 "계산 자세히": 세금 나눔, 기록 없음과 차곡 비교표, 가정. 탭과 같은 입력값을 읽는다 */
@Composable
internal fun SimulatorDetailSheet(
    onDismiss: () -> Unit,
    viewModel: SimulatorViewModel = viewModel(factory = SimulatorViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val comparison = state.comparison
    SheetOverlay(
        onDismiss = onDismiss,
        header = {
            Text(stringResource(R.string.sim_detail_title), fontSize = TextSizes.title, fontWeight = FontWeight.Bold)
        },
    ) {
        Row(Modifier.padding(vertical = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.xxl)) {
            FactCell(R.string.sim_fact_corp, Formatters.money(comparison.corpTaxSaved))
            FactCell(R.string.sim_fact_income, Formatters.money(comparison.incomeTaxSaved))
            FactCell(R.string.sim_fact_total, Formatters.money(comparison.totalCost))
        }
        ComparisonSection(state.ownership, comparison)
        SourceText(stringResource(R.string.sim_disclaimer))
    }
}
