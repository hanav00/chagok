package kr.chagok.ui.table

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kr.chagok.R
import kr.chagok.domain.model.DistanceTotals
import kr.chagok.ui.components.FactCell
import kr.chagok.ui.components.RuledDivider
import kr.chagok.ui.components.RuledRow
import kr.chagok.ui.components.ruledCell
import kr.chagok.ui.format.Formatters

// ⑪⑫⑬ 사업연도 합계. 핵심 칸은 ⑬업무사용비율
@Composable
internal fun YearTotals(totals: DistanceTotals) {
    RuledRow {
        FactCell(
            label = R.string.form_year_total,
            value = stringResource(R.string.km_value, Formatters.km(totals.total)),
            modifier = Modifier.weight(1f).ruledCell(isFirst = true),
        )
        RuledDivider()
        FactCell(
            label = R.string.form_year_business,
            value = stringResource(R.string.km_value, Formatters.km(totals.businessUseKm)),
            modifier = Modifier.weight(1f).ruledCell(isFirst = false),
        )
        RuledDivider()
        FactCell(
            label = R.string.form_year_ratio,
            value = Formatters.percent(totals.businessRatio),
            modifier = Modifier.weight(1f).ruledCell(isFirst = false),
            isKey = true,
        )
    }
}
