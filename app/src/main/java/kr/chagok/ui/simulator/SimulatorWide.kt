package kr.chagok.ui.simulator

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.domain.tax.Ownership
import kr.chagok.domain.tax.TaxComparison
import kr.chagok.domain.tax.TaxConfig
import kr.chagok.ui.components.FlightEasing
import kr.chagok.ui.components.FlightSpots
import kr.chagok.ui.components.HeroNumber
import kr.chagok.ui.components.Hint
import kr.chagok.ui.components.IntroClock
import kr.chagok.ui.components.MoreButton
import kr.chagok.ui.components.SegmentedControl
import kr.chagok.ui.components.WideGrid
import kr.chagok.ui.components.WideHead
import kr.chagok.ui.components.dashedOutline
import kr.chagok.ui.components.flightRoot
import kr.chagok.ui.components.flightSpot
import kr.chagok.ui.components.flying
import kr.chagok.ui.components.rememberIntroClock
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes
import kotlin.math.PI
import kotlin.math.cos

private val BAR_WIDTH = 200.dp
private val BAR_MIN_HEIGHT = 64.dp
private val BAR_GAP = Spacing.xxxl
private const val BAR_GROW_MILLIS = 700
private const val BAR_STAGGER_MILLIS = 260

/** "230만 원" 을 큰 숫자와 작은 단위로 나눈다. 억 단위만 있으면("1억 원") 통째로 숫자 자리에 */
private const val MAN_WON = "만 원"

// 처음 열 때: 막대가 다 자라면 기록 없음 막대에서 차곡보다 긴 몫이 깜박이고, 떨어져 나와 큰 숫자로 들어간 뒤 숫자가 올라간다
private const val MARK_AT = 1100
private const val MARK_MILLIS = 700
private const val MARK_BLINKS = 2
private const val CUT_AT = 1800
private const val CUT_MILLIS = 650
private const val COUNT_AT = 2450
private const val COUNT_MILLIS = 900
private const val INTRO_MILLIS = 3350
private const val PIECE_END_SCALE_X = 0.08f
private const val PIECE_END_SCALE_Y = 0.4f
private const val PIECE_FADE_FROM = 0.7f

/** 차곡 막대보다 이만큼도 길지 않으면 몫을 나눠 그리지 않는다 */
private const val MIN_CUT_SHARE = 0.03f

private enum class CutSpot { PIECE, HERO }

/**
 * 넓은 창 절세 계산. 위 줄은 차량 종류 | 줄어드는 세금, 아래 줄은 비용 − + | 상여로 넘어가는 몫 두 막대.
 * 비교표·가정은 "계산 자세히" 시트로 보낸다.
 */
@Composable
internal fun SimulatorWide(state: SimulatorUiState, viewModel: SimulatorViewModel, onOpenDetail: () -> Unit) {
    val hasCut = state.headline == SimulatorHeadline.SAVED && cutShareOf(state.comparison) > 0f
    val intro = rememberIntroClock(Unit, INTRO_MILLIS, isDone = !hasCut)
    val spots = remember { FlightSpots() }
    LaunchedEffect(Unit) { intro.play() }
    Box(Modifier.flightRoot(spots)) {
        SimulatorGrid(state, viewModel, intro, spots, onOpenDetail)
        CutPiece(intro, spots)
    }
}

@Composable
private fun SimulatorGrid(
    state: SimulatorUiState,
    viewModel: SimulatorViewModel,
    intro: IntroClock,
    spots: FlightSpots,
    onOpenDetail: () -> Unit,
) {
    WideGrid(
        topSide = {
            Text(stringResource(R.string.sim_vehicle), color = ChagokColors.textSub)
            SegmentedControl(
                options = Ownership.entries,
                selected = state.ownership,
                label = { stringResource(it.labelRes) },
                onSelect = viewModel::selectOwnership,
                modifier = Modifier.padding(top = Spacing.m),
            )
        },
        topMain = {
            val count = FastOutSlowInEasing.transform(intro.at(COUNT_AT, COUNT_MILLIS))
            SavedHero(state.headline, state.comparison, count, Modifier.flightSpot(spots, CutSpot.HERO))
        },
        bottomSide = {
            CostStep(stringResource(state.ownership.inputLabelRes), state.carInput, viewModel::stepCarInput)
            CostStep(stringResource(R.string.sim_other_short), state.otherCost, viewModel::stepOtherCost)
            if (state.isExampleRatio) {
                val ratio = Formatters.percent(TaxConfig.EXAMPLE_BUSINESS_RATIO, digits = 0)
                Hint(stringResource(R.string.sim_example_ratio, ratio))
            }
        },
        bottomMain = { BonusBars(state.comparison, intro, spots, onOpenDetail) },
    )
}

@Composable
private fun SavedHero(headline: SimulatorHeadline, comparison: TaxComparison, count: Float, numberModifier: Modifier) {
    when (headline) {
        SimulatorHeadline.SAVED -> {
            val saved = Formatters.money(comparison.taxSaved * count)
            val isMan = saved.endsWith(MAN_WON)
            HeroNumber(
                label = stringResource(R.string.sim_hero_label),
                number = if (isMan) saved.removeSuffix(MAN_WON) else saved,
                unit = if (isMan) MAN_WON else "",
                numberModifier = numberModifier,
            ) {
                Text(
                    stringResource(
                        R.string.sim_split,
                        Formatters.money(comparison.corpTaxSaved),
                        Formatters.money(comparison.incomeTaxSaved),
                    ),
                    color = ChagokColors.textSub,
                )
            }
        }
        SimulatorHeadline.UNDER_LIMIT ->
            ReasonLine(stringResource(R.string.sim_under_limit, Formatters.money(TaxConfig.NO_LOG_LIMIT)))
        SimulatorHeadline.NO_LOG_BETTER -> ReasonLine(stringResource(R.string.sim_no_log_better))
    }
}

@Composable
private fun CostStep(label: String, value: Int, onStep: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(label, color = ChagokColors.textSub)
        MoneyStepRow(label = label, value = Formatters.money(value.toDouble()), onStep = onStep)
    }
}

// 기록 없음과 차곡에서 사용자 상여로 넘어가는 몫. 한 가지만 비교해 한눈에 읽히게 한다
@Composable
private fun ColumnScope.BonusBars(
    comparison: TaxComparison,
    intro: IntroClock,
    spots: FlightSpots,
    onOpenDetail: () -> Unit,
) {
    val noLog = comparison.noLog.bonus
    val withLog = comparison.withLog.bonus
    val max = maxOf(noLog, withLog).takeIf { it > 0 } ?: 1.0
    WideHead(stringResource(R.string.sim_bonus_title)) {
        MoreButton(stringResource(R.string.sim_detail), onClick = onOpenDetail)
    }
    Row(
        Modifier.weight(1f).padding(top = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(BAR_GAP),
    ) {
        BonusBar(
            value = noLog,
            fraction = (noLog / max).toFloat(),
            label = stringResource(R.string.sim_col_no_log),
            color = ChagokColors.cardStrong,
            textColor = ChagokColors.text,
            delayMillis = 0,
            cut = BarCut(
                share = cutShareOf(comparison),
                mark = markOf(intro.at(MARK_AT, MARK_MILLIS)),
                isGone = intro.at(CUT_AT, CUT_MILLIS) > 0f,
                modifier = Modifier.flightSpot(spots, CutSpot.PIECE),
            ),
        )
        BonusBar(
            value = withLog,
            fraction = (withLog / max).toFloat(),
            label = stringResource(R.string.sim_col_with_log),
            color = ChagokColors.accent,
            textColor = ChagokColors.onAccent,
            delayMillis = BAR_STAGGER_MILLIS,
        )
    }
    Text(
        stringResource(
            R.string.sim_bonus_line,
            Formatters.percent(comparison.noLog.ratio),
            Formatters.percent(comparison.withLog.ratio),
        ),
        Modifier.padding(top = Spacing.l),
        color = ChagokColors.textSub,
    )
}

/**
 * 기록 없음 막대에서 차곡 막대보다 긴 몫 (기록하면 상여로 안 넘어가는 몫).
 * [mark] 는 점선 테두리 진하기, [isGone] 이면 조각이 빠져나가 점선 빈칸만 남는다.
 */
private class BarCut(val share: Float, val mark: Float, val isGone: Boolean, val modifier: Modifier)

@Composable
private fun BonusBar(
    value: Double,
    fraction: Float,
    label: String,
    color: Color,
    textColor: Color,
    delayMillis: Int,
    cut: BarCut? = null,
) {
    val grown = remember { Animatable(0f) }
    LaunchedEffect(fraction) { grown.animateTo(fraction, tween(BAR_GROW_MILLIS, delayMillis)) }
    Column(Modifier.width(BAR_WIDTH).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(grown.value.coerceIn(0f, 1f))
                    .heightIn(min = BAR_MIN_HEIGHT),
            ) {
                if (cut != null && cut.share > 0f) {
                    Column(Modifier.matchParentSize(), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        CutPart(cut, color, Modifier.weight(cut.share))
                        Box(Modifier.weight(1f - cut.share).fillMaxWidth().background(color, Shapes.control))
                    }
                } else {
                    Box(Modifier.matchParentSize().background(color, Shapes.control))
                }
                Text(
                    Formatters.money(value),
                    Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                    color = textColor,
                    fontSize = TextSizes.subtitle,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
        Text(label, color = ChagokColors.textSub)
    }
}

@Composable
private fun CutPart(cut: BarCut, color: Color, modifier: Modifier) {
    val look = if (cut.isGone) {
        Modifier.dashedOutline(ChagokColors.textDim, Shapes.control)
    } else {
        Modifier
            .background(color, Shapes.control)
            .dashedOutline(ChagokColors.accent.copy(alpha = cut.mark), Shapes.control)
    }
    Box(modifier.fillMaxWidth().then(cut.modifier).then(look))
}

/** 기록 없음 막대에서 빠져나와 큰 숫자로 들어가는 조각. 나는 동안만 그린다 */
@Composable
private fun CutPiece(intro: IntroClock, spots: FlightSpots) {
    val progress = intro.at(CUT_AT, CUT_MILLIS)
    val from = spots[CutSpot.PIECE]
    val to = spots[CutSpot.HERO]
    if (from == null || to == null || progress <= 0f || progress >= 1f) return
    val size = with(LocalDensity.current) { DpSize(from.width.toDp(), from.height.toDp()) }
    Box(
        Modifier
            .flying(
                from = from,
                to = to,
                progress = FlightEasing.transform(progress),
                endScaleX = PIECE_END_SCALE_X,
                endScaleY = PIECE_END_SCALE_Y,
            )
            .size(size)
            .graphicsLayer { alpha = 1f - ((progress - PIECE_FADE_FROM) / (1f - PIECE_FADE_FROM)).coerceIn(0f, 1f) }
            .background(ChagokColors.accent, Shapes.control),
    )
}

private fun cutShareOf(comparison: TaxComparison): Float {
    val noLog = comparison.noLog.bonus
    val withLog = comparison.withLog.bonus
    if (noLog <= 0.0 || withLog >= noLog) return 0f
    return ((noLog - withLog) / noLog).toFloat().takeIf { it >= MIN_CUT_SHARE } ?: 0f
}

// 처음엔 안 보이다가 진하게 → 두 번 깜박인 뒤 진하게 남는다
private fun markOf(t: Float): Float = if (t <= 0f) 0f else 0.5f + 0.5f * cos(2 * PI * MARK_BLINKS * t).toFloat()
