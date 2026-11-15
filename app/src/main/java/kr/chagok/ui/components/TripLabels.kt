package kr.chagok.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Work
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import kr.chagok.R
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.domain.model.Trip
import kr.chagok.ui.theme.ChagokColors

// 분류·운행을 화면 글자와 색으로 바꾸는 곳. 화면마다 따로 만들지 않는다

@get:StringRes
val Category.labelRes: Int
    get() = when (this) {
        Category.COMMUTE -> R.string.category_commute
        Category.BUSINESS -> R.string.category_business
        Category.PERSONAL -> R.string.category_personal
    }

@get:StringRes
val Category.shortLabelRes: Int
    get() = when (this) {
        Category.COMMUTE -> R.string.category_commute_short
        Category.BUSINESS -> R.string.category_business_short
        Category.PERSONAL -> R.string.category_personal_short
    }

/** 주행 중 한 줄 ("업무로 기록되고 있어요"). 주행 화면과 첫 실행 "이렇게 기록돼요"가 같이 쓴다 */
@get:StringRes
val Category.drivingLabelRes: Int
    get() = when (this) {
        Category.PERSONAL -> R.string.drive_personal
        Category.COMMUTE -> R.string.drive_commute
        Category.BUSINESS -> R.string.drive_business
    }

/** 다음 출발 이름 ("업무" · "개인", Gleo 로 고른 출퇴근은 "출퇴근"). 토글·안내 줄·연습·설정 기본 분류가 같이 쓴다 */
@get:StringRes
val Category.departureLabelRes: Int
    get() = when (this) {
        Category.BUSINESS -> R.string.next_business
        Category.COMMUTE -> R.string.next_commute
        Category.PERSONAL -> R.string.next_personal
    }

/** 다음 출발 토글 아이콘. 출퇴근은 업무 쪽 아이콘이다 (토글에서 업무 칸이 켜진다) */
val Category.departureIcon: ImageVector
    get() = if (isBusinessUse) Icons.Rounded.Work else Icons.Rounded.DirectionsCar

/** 분류 색 (막대·점·칩). null 은 분류 대기 */
val Category?.color: Color
    get() = when (this) {
        Category.COMMUTE -> ChagokColors.commute
        Category.BUSINESS -> ChagokColors.business
        Category.PERSONAL -> ChagokColors.personal
        null -> ChagokColors.pending
    }

/** 어두운 바탕 위 분류 글자색 (작은 분류 버튼) */
val Category.textColor: Color
    get() = when (this) {
        Category.COMMUTE -> ChagokColors.commute
        Category.BUSINESS -> ChagokColors.business
        Category.PERSONAL -> ChagokColors.personalText
    }

@get:StringRes
private val ClassifyReason.labelRes: Int
    get() = when (this) {
        ClassifyReason.DEFAULT_BUSINESS -> R.string.reason_default_business
        ClassifyReason.DEFAULT_PERSONAL -> R.string.reason_default_personal
        ClassifyReason.PRE_PICK -> R.string.reason_pre_pick
        ClassifyReason.USER_EDIT -> R.string.reason_user_edit
        ClassifyReason.USER_PICK -> R.string.reason_user_pick
        ClassifyReason.ADMIN_EDIT -> R.string.reason_admin_edit
    }

/** 운행 상세 "분류 근거". 출퇴근이면 "· 집↔회사"를 붙인다. 근거가 없으면 null */
@Composable
fun reasonLabel(trip: Trip): String? {
    val reason = trip.reason ?: return null
    val label = stringResource(reason.labelRes)
    return if (trip.category == Category.COMMUTE) stringResource(R.string.reason_commute_suffix, label) else label
}

/**
 * 운행 이름 (기록부 ⑩비고·방금 운행 제목·분류 대기 줄).
 * 개인 운행은 장소 없이 "개인 사용", 내비 없이 간 업무 운행은 "내비 미사용 · 주소".
 */
@Composable
fun tripTitle(trip: Trip): String {
    val destination = trip.destination
    return when {
        trip.isGap -> stringResource(R.string.trip_gap)
        trip.isPersonal -> stringResource(R.string.trip_personal)
        destination == null -> stringResource(R.string.trip_unknown_place)
        destination.hasName -> destination.name
        destination.address.isBlank() -> stringResource(R.string.trip_no_navi_bare) // 내비가 주소를 못 줬을 때
        else -> stringResource(R.string.trip_no_navi, destination.address)
    }
}
