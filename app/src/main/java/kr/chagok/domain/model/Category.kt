package kr.chagok.domain.model

/**
 * 운행 분류. 국세청 별지 서식의 ⑧출·퇴근 · ⑨일반 업무, 그리고 업무용 사용거리에 들어가지 않는 개인.
 * 출퇴근도 업무용 거리라 ⑬업무사용비율을 바꾸는 것은 "개인이냐 아니냐"뿐이다.
 */
enum class Category {
    COMMUTE,
    BUSINESS,
    PERSONAL;

    val isBusinessUse: Boolean get() = this != PERSONAL

    companion object {
        /** 운행 상세에서 고르는 버튼 순서 */
        val PICK_ORDER = listOf(BUSINESS, COMMUTE, PERSONAL)

        /** 기록 안 된 주행에서 고르는 값. 시각·장소를 몰라 출퇴근인지 알 수 없다 */
        val GAP_CHOICES = listOf(BUSINESS, PERSONAL)

        /** 기본 분류·다음 출발 토글에서 고를 수 있는 값 (출퇴근은 집·회사로 저절로 나뉜다) */
        val DEPARTURE_CHOICES = listOf(BUSINESS, PERSONAL)

        /** 고르는 운행에 맞는 버튼 목록 */
        fun choicesFor(trip: Trip): List<Category> = if (trip.isGap) GAP_CHOICES else PICK_ORDER
    }
}
