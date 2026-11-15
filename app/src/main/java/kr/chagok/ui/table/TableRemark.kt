package kr.chagok.ui.table

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import kr.chagok.R
import kr.chagok.domain.model.Trip
import kr.chagok.ui.components.tripTitle
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.AppFontFamily
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

private const val REMARK_LINE_HEIGHT = 1.35f
private val REMARK_TEXT = TextStyle(
    fontFamily = AppFontFamily,
    fontSize = TextSizes.body,
    lineHeight = TextSizes.body * REMARK_LINE_HEIGHT,
)
private val CHIP_GAP = Spacing.xxs

/**
 * ⑩비고 칸: 운행 이름과 방문 목적. 개인 운행은 장소를 적지 않고 "개인 사용"만 둔다.
 * 기록 안 된 주행은 누가 탔는지 모르니 "④사용자 확인 필요"를 덧붙인다.
 */
@Composable
internal fun Remark(row: TableRow) {
    val trip = row.trip
    when {
        trip.isPending -> Row(
            horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PendingChip(stringResource(R.string.category_pending))
            Text(tripTitle(trip), style = REMARK_TEXT)
        }
        trip.isPersonal -> Text(
            text = stringResource(R.string.trip_personal),
            color = ChagokColors.textSub,
            style = REMARK_TEXT,
        )
        trip.isGap -> Text(gapRemark(trip), style = REMARK_TEXT)
        else -> Text(remarkText(trip), style = REMARK_TEXT)
    }
}

/** ⑩비고 글자만 (칩·색 없이). 방금 운행 카드의 서식 한 줄이 쓴다 */
@Composable
internal fun remarkText(trip: Trip): String = when {
    trip.isPersonal -> stringResource(R.string.trip_personal)
    trip.isGap -> withNote(stringResource(R.string.trip_gap), trip)
    else -> withNote(tripTitle(trip), trip)
}

@Composable
private fun gapRemark(trip: Trip): AnnotatedString {
    val check = stringResource(R.string.table_gap_user_check)
    val text = withNote(stringResource(R.string.trip_gap) + Formatters.SEPARATOR + check, trip)
    val start = text.indexOf(check)
    return buildAnnotatedString {
        append(text)
        addStyle(SpanStyle(color = ChagokColors.textSub), start, start + check.length)
    }
}

private fun withNote(text: String, trip: Trip): String =
    if (trip.note.isBlank()) text else text + Formatters.SEPARATOR + trip.note
