package kr.chagok.ui.simulator

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.chagok.R
import kr.chagok.domain.tax.Ownership
import kr.chagok.domain.tax.TaxComparison
import kr.chagok.domain.tax.TaxConfig
import kr.chagok.ui.components.Hint
import kr.chagok.ui.components.ScreenColumn
import kr.chagok.ui.components.Section
import kr.chagok.ui.components.SectionTitle
import kr.chagok.ui.components.SegmentedControl
import kr.chagok.ui.components.SourceText
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

private val SPLIT_GAP = Spacing.xxs
private val INPUT_GAP = Spacing.xxs
private val INPUT_TOP = Spacing.xxs

/**
 * 절세 계산 탭.
 * 줄어드는 세금을 맨 위에 둬서 비용을 조절하는 동안에도 보이게 하고, 그 아래 입력 · 비교표 순서.
 * 넓은 창은 줄어드는 세금과 상여 막대 두 개만 두고, 비교표는 "계산 자세히" 시트로 본다.
 */
@Composable
fun SimulatorScreen(
    isWide: Boolean,
    onOpenDetail: () -> Unit,
    viewModel: SimulatorViewModel = viewModel(factory = SimulatorViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (isWide) {
        SimulatorWide(state, viewModel, onOpenDetail)
        return
    }
    ScreenColumn {
        Headline(state.headline, state.comparison)
        if (state.isExampleRatio) {
            val ratio = Formatters.percent(TaxConfig.EXAMPLE_BUSINESS_RATIO, digits = 0)
            Hint(stringResource(R.string.sim_example_ratio, ratio))
        }
        CostSection(
            state = state,
            onSelectOwnership = viewModel::selectOwnership,
            onCarInput = viewModel::setCarInput,
            onStepCarInput = viewModel::stepCarInput,
            onOtherCost = viewModel::setOtherCost,
            onStepOtherCost = viewModel::stepOtherCost,
        )
        ComparisonSection(state.ownership, state.comparison)
        SourceText(stringResource(R.string.sim_disclaimer))
    }
}

// 줄어드는 세금. 기록이 유리하지 않으면 왜 그런지 한 줄로
@Composable
private fun Headline(headline: SimulatorHeadline, comparison: TaxComparison) {
    Section(gap = SPLIT_GAP) {
        when (headline) {
            SimulatorHeadline.SAVED -> SavedLines(comparison)
            SimulatorHeadline.UNDER_LIMIT ->
                ReasonLine(stringResource(R.string.sim_under_limit, Formatters.money(TaxConfig.NO_LOG_LIMIT)))
            SimulatorHeadline.NO_LOG_BETTER -> ReasonLine(stringResource(R.string.sim_no_log_better))
        }
    }
}

@Composable
private fun SavedLines(comparison: TaxComparison) {
    val saved = Formatters.money(comparison.taxSaved)
    Text(
        text = emphasize(stringResource(R.string.sim_saved, saved), saved, AMOUNT_STYLE),
        fontSize = TextSizes.title,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = stringResource(
            R.string.sim_split,
            Formatters.money(comparison.corpTaxSaved),
            Formatters.money(comparison.incomeTaxSaved),
        ),
        color = ChagokColors.textSub,
    )
}

@Composable
internal fun ReasonLine(text: String) {
    Text(text, color = ChagokColors.textSub, fontSize = TextSizes.subtitle, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun CostSection(
    state: SimulatorUiState,
    onSelectOwnership: (Ownership) -> Unit,
    onCarInput: (Float) -> Unit,
    onStepCarInput: (direction: Int) -> Unit,
    onOtherCost: (Float) -> Unit,
    onStepOtherCost: (direction: Int) -> Unit,
) {
    val ownership = state.ownership
    Section {
        SectionTitle(stringResource(R.string.sim_cost_title))
        SegmentedControl(
            options = Ownership.entries,
            selected = ownership,
            label = { stringResource(it.labelRes) },
            onSelect = onSelectOwnership,
        )
        val carLabel = stringResource(ownership.inputLabelRes)
        MoneyInputBlock(label = carLabel, value = carValueText(ownership, state.carInput, state.yearlyCarCost)) {
            MoneyStepper(
                input = ownership.input,
                value = state.carInput,
                label = carLabel,
                onValueChange = onCarInput,
                onStep = onStepCarInput,
            )
        }
        val otherLabel = stringResource(R.string.sim_other_short)
        MoneyInputBlock(label = otherLabel, value = AnnotatedString(Formatters.money(state.otherCost.toDouble()))) {
            MoneyStepper(
                input = TaxConfig.OTHER_COST_INPUT,
                value = state.otherCost,
                label = otherLabel,
                onValueChange = onOtherCost,
                onStep = onStepOtherCost,
            )
        }
    }
}

// 입력 줄: 이름 · 지금 값 · 슬라이더
@Composable
private fun MoneyInputBlock(label: String, value: AnnotatedString, stepper: @Composable () -> Unit) {
    Column(Modifier.padding(top = INPUT_TOP), verticalArrangement = Arrangement.spacedBy(INPUT_GAP)) {
        Text(label, color = ChagokColors.textSub)
        Text(value, fontSize = TextSizes.headline, fontWeight = FontWeight.ExtraBold)
        stepper()
    }
}

// "150만 원 · 연 1,800만 원": 뒤쪽 설명은 작고 흐리게
@Composable
private fun carValueText(ownership: Ownership, value: Int, yearlyCost: Double): AnnotatedString {
    val amount = Formatters.money(value.toDouble())
    val detailRes = if (ownership.isMonthly) R.string.sim_car_monthly else R.string.sim_car_buy
    val detail = stringResource(detailRes, Formatters.money(yearlyCost))
    return buildAnnotatedString {
        append(amount)
        append(" ")
        pushStyle(DETAIL_STYLE)
        append(detail)
        pop()
    }
}

/** 문장 안의 [part] 만 [style] 로 */
private fun emphasize(sentence: String, part: String, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(sentence)
    val start = sentence.indexOf(part)
    if (start >= 0) addStyle(style, start, start + part.length)
}

private val AMOUNT_STYLE = SpanStyle(
    color = ChagokColors.text,
    fontSize = TextSizes.headline,
    fontWeight = FontWeight.Bold,
)
private val DETAIL_STYLE = SpanStyle(
    color = ChagokColors.textSub,
    fontSize = TextSizes.body,
    fontWeight = FontWeight.Medium,
)

@get:StringRes
internal val Ownership.labelRes: Int
    get() = when (this) {
        Ownership.LEASE -> R.string.sim_ownership_lease
        Ownership.RENT -> R.string.sim_ownership_rent
        Ownership.BUY -> R.string.sim_ownership_buy
    }

@get:StringRes
internal val Ownership.inputLabelRes: Int
    get() = when (this) {
        Ownership.LEASE -> R.string.sim_input_lease
        Ownership.RENT -> R.string.sim_input_rent
        Ownership.BUY -> R.string.sim_input_buy
    }
