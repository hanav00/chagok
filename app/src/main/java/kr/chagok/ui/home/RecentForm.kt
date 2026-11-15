package kr.chagok.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.domain.model.Person
import kr.chagok.domain.model.Trip
import kr.chagok.ui.components.FlightEasing
import kr.chagok.ui.components.FlightSpots
import kr.chagok.ui.components.IntroClock
import kr.chagok.ui.components.ScatterText
import kr.chagok.ui.components.flightSpot
import kr.chagok.ui.components.flying
import kr.chagok.ui.components.rememberIntroClock
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.table.BODY_TEXT
import kr.chagok.ui.table.FormColumn
import kr.chagok.ui.table.HEADER_TEXT
import kr.chagok.ui.table.TableRow
import kr.chagok.ui.table.remarkText
import kr.chagok.ui.table.tripCells
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

// 블록이 내려앉은 뒤 순서 (ms): 계기판 줄 → 서식 한 줄 → 두 숫자가 ⑤⑥ 칸으로 → 나머지 칸
private const val ODOMETER_AT = 600
private const val ODOMETER_FADE_MILLIS = 200
private const val ROLL_AT = 700
private const val ROLL_MILLIS = 800
private const val ROW_AT = 1500
private const val ROW_FADE_MILLIS = 300
private const val LEFT_CELLS_AT = 1700
private const val RIGHT_CELLS_AT = 3200
private const val CELL_MILLIS = 260
private const val CELL_STAGGER_MILLIS = 130
private const val FLY_START_AT = 2200
private const val FLY_END_AT = 2360
private const val FLY_MILLIS = 720
private const val HIT_MILLIS = 1300
private const val TOTAL_MILLIS = 4600

private val FLY_ARC = 56.dp
private val CELL_RISE = 10.dp
private val HIT_STROKE = 3.dp
private val VALUE_HEIGHT = 52.dp

/** 이보다 좁으면 ⑤⑥⑦⑩ 네 칸만 */
private val FULL_FORM_MIN_WIDTH = 760.dp
private const val NUMBER_WEIGHT = 1.2f
private const val REMARK_WEIGHT = 1.6f

private val ODOMETER_NUMBER = BODY_TEXT.copy(fontSize = TextSizes.title, fontWeight = FontWeight.ExtraBold)
private val VALUE_TEXT = BODY_TEXT.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

private val FULL_COLUMNS = FormColumn.entries
private val SHORT_COLUMNS = listOf(FormColumn.START_KM, FormColumn.END_KM, FormColumn.KM)

private enum class Spot { START_FROM, START_TO, END_FROM, END_TO }

/** 방금 운행 카드가 처음 뜰 때 한 번 도는 순서. 이미 본 운행은 끝난 모습으로 시작한다 */
@Composable
internal fun rememberRecentIntro(tripId: Long, isLanded: Boolean): IntroClock =
    rememberIntroClock(tripId, TOTAL_MILLIS, isDone = isLanded)

/** "계기판 23,419.0 → 23,431.0 km". 끝 숫자는 처음에 시작값에서 굴러 올라간다 */
@Composable
internal fun OdometerLine(trip: Trip, intro: IntroClock, spots: FlightSpots) {
    val roll = FastOutSlowInEasing.transform(intro.at(ROLL_AT, ROLL_MILLIS))
    Row(
        modifier = Modifier.graphicsLayer { alpha = intro.at(ODOMETER_AT, ODOMETER_FADE_MILLIS) },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Speed, contentDescription = null, tint = ChagokColors.textSub)
        Text(stringResource(R.string.recent_odometer), color = ChagokColors.textSub)
        Text(
            Formatters.km(trip.startKm),
            style = ODOMETER_NUMBER,
            color = ChagokColors.text,
            modifier = Modifier.flightSpot(spots, Spot.START_FROM),
        )
        Text(stringResource(R.string.arrow), color = ChagokColors.textSub)
        Text(
            Formatters.km(trip.startKm + trip.km * roll),
            style = ODOMETER_NUMBER,
            color = ChagokColors.text,
            modifier = Modifier.flightSpot(spots, Spot.END_FROM),
        )
        Text(stringResource(R.string.km_unit), color = ChagokColors.textSub)
    }
}

/**
 * 이 운행이 기록부에 적힌 모습 그대로의 서식 한 줄. 칸 값은 기록부와 같은 [tripCells] 로 만든다.
 * 개인으로 바꾸면 ⑩비고의 장소 글자가 흩어지고 "개인 사용"이 된다.
 */
@Composable
internal fun RecentFormLine(trip: Trip, person: Person?, isUnassigned: Boolean, intro: IntroClock, spots: FlightSpots) {
    val cells = tripCells(TableRow(trip, person, isUnassigned))
    val remark = remarkText(trip)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = intro.at(ROW_AT, ROW_FADE_MILLIS) }
            .background(ChagokColors.section, Shapes.control)
            .padding(Spacing.s),
    ) {
        val columns = if (maxWidth >= FULL_FORM_MIN_WIDTH) FULL_COLUMNS else SHORT_COLUMNS
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Row(verticalAlignment = Alignment.Bottom) {
                columns.forEach { HeaderCell(stringResource(it.headerRes), weightOf(it)) }
                HeaderCell(stringResource(R.string.form_remark), REMARK_WEIGHT)
            }
            Row {
                columns.forEachIndexed { i, column ->
                    val cell = cells[column]
                    ValueCell(weight = weightOf(column), appear = appearOf(column, i, columns, intro), hit = hitOf(column, intro)) {
                        ScatterText(
                            text = cell?.text.orEmpty(),
                            isErasing = false,
                            style = VALUE_TEXT,
                            color = colorOf(column, cell?.isChip == true),
                            modifier = targetOf(column)?.let { Modifier.flightSpot(spots, it) } ?: Modifier,
                        )
                    }
                }
                ValueCell(weight = REMARK_WEIGHT, appear = intro.at(RIGHT_CELLS_AT + CELL_STAGGER_MILLIS * 3, CELL_MILLIS), hit = 0f) {
                    ScatterText(
                        text = remark,
                        isErasing = trip.isPersonal,
                        style = VALUE_TEXT,
                        color = if (trip.isPersonal) ChagokColors.textSub else ChagokColors.text,
                    )
                }
            }
        }
    }
}

/** 계기판 줄의 두 숫자가 ⑤⑥ 칸으로 날아가는 동안만 그린다. 카드 위에 겹쳐 놓는다 */
@Composable
internal fun RecentFlyers(trip: Trip, intro: IntroClock, spots: FlightSpots) {
    Flyer(Formatters.km(trip.startKm), spots[Spot.START_FROM], spots[Spot.START_TO], intro.at(FLY_START_AT, FLY_MILLIS))
    Flyer(Formatters.km(trip.endKm), spots[Spot.END_FROM], spots[Spot.END_TO], intro.at(FLY_END_AT, FLY_MILLIS))
}

@Composable
private fun Flyer(text: String, from: Rect?, to: Rect?, progress: Float) {
    if (from == null || to == null || progress <= 0f || progress >= 1f) return
    Text(
        text = text,
        style = ODOMETER_NUMBER,
        color = ChagokColors.businessText,
        softWrap = false,
        modifier = Modifier.flying(
            from = from,
            to = to,
            progress = FlightEasing.transform(progress),
            arc = FLY_ARC,
            endScaleX = to.height / from.height,
        ),
    )
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(text, Modifier.weight(weight), style = HEADER_TEXT.copy(textAlign = TextAlign.Center))
}

@Composable
private fun RowScope.ValueCell(weight: Float, appear: Float, hit: Float, content: @Composable () -> Unit) {
    Box(
        Modifier
            .weight(weight)
            .height(VALUE_HEIGHT)
            .border(HIT_STROKE, ChagokColors.accent.copy(alpha = hit), Shapes.block)
            .graphicsLayer {
                alpha = appear
                translationY = CELL_RISE.toPx() * (1f - appear)
            }
            .padding(horizontal = Spacing.xxs),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun weightOf(column: FormColumn): Float = if (column.isNumber) NUMBER_WEIGHT else 1f

private fun targetOf(column: FormColumn): Spot? = when (column) {
    FormColumn.START_KM -> Spot.START_TO
    FormColumn.END_KM -> Spot.END_TO
    else -> null
}

// ⑤⑥은 날아온 숫자가 닿는 순간 나타난다. 왼쪽 칸은 줄이 뜰 때, 오른쪽 칸은 숫자가 꽂힌 뒤 차례로
private fun appearOf(column: FormColumn, index: Int, columns: List<FormColumn>, intro: IntroClock): Float = when (column) {
    FormColumn.START_KM -> landed(intro.at(FLY_START_AT, FLY_MILLIS))
    FormColumn.END_KM -> landed(intro.at(FLY_END_AT, FLY_MILLIS))
    else -> {
        val afterEnd = index > columns.indexOf(FormColumn.END_KM)
        val start = if (afterEnd) RIGHT_CELLS_AT else LEFT_CELLS_AT
        val order = if (afterEnd) index - columns.indexOf(FormColumn.END_KM) - 1 else index
        intro.at(start + CELL_STAGGER_MILLIS * order, CELL_MILLIS)
    }
}

private fun landed(flight: Float): Float = if (flight >= 1f) 1f else 0f

// 숫자가 꽂힌 칸에 잠깐 테두리
private fun hitOf(column: FormColumn, intro: IntroClock): Float {
    val landAt = when (column) {
        FormColumn.START_KM -> FLY_START_AT + FLY_MILLIS
        FormColumn.END_KM -> FLY_END_AT + FLY_MILLIS
        else -> return 0f
    }
    val t = intro.at(landAt, HIT_MILLIS)
    return if (t <= 0f) 0f else 1f - t
}

private fun colorOf(column: FormColumn, isChip: Boolean) = when {
    isChip -> ChagokColors.pendingText
    column == FormColumn.BUSINESS -> ChagokColors.businessText
    else -> ChagokColors.text
}
