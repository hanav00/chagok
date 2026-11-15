package kr.chagok.domain.model

/**
 * 분류 근거. 운행 상세의 "분류 근거" 칸과 엑셀로 나간다.
 * 기본값 그대로인 운행과 사람이 정한 운행을 구분해, 세무 소명 때 누가 정했는지 숨기지 않는다.
 */
enum class ClassifyReason {
    DEFAULT_BUSINESS,
    DEFAULT_PERSONAL,

    /** 출발 전 홈 "다음 출발" 토글로 기본값에서 바꿈 */
    PRE_PICK,

    /** 도착 후·기록부에서 사용자가 고침 */
    USER_EDIT,

    /** 기록 안 된 주행을 사용자가 고름 */
    USER_PICK,

    /** 관리자 PIN 으로 연 상태에서 고침 */
    ADMIN_EDIT;

    /** 기본값 그대로가 아니라 사람이 정한 분류인지 (방금 운행 카드에 근거를 덧붙이는 기준) */
    val isChangedByUser: Boolean get() = this == PRE_PICK || this == USER_EDIT || this == ADMIN_EDIT

    companion object {
        fun defaultOf(category: Category): ClassifyReason =
            if (category == Category.PERSONAL) DEFAULT_PERSONAL else DEFAULT_BUSINESS
    }
}
