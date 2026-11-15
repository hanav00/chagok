package kr.chagok.domain

/**
 * 차곡 규칙 숫자. 숫자는 여기만 고친다.
 * 절세 계산 숫자는 [TaxConfig], 화면 크기·색은 ui/theme.
 */
object ChagokConfig {
    /** 앞 운행 종료 ~ 지금 계기판 차이가 이 이상이면 "기록 안 된 주행" (km) */
    const val GAP_MIN_KM = 0.1

    /**
     * 계기판 값끼리 뺄 때 생기는 부동소수 오차 (km). 2048.4 − 2048.3 은 0.0999… 가 되어
     * 그대로 [GAP_MIN_KM] 과 견주면 계기판 한 칸(0.1km)짜리 빠진 주행을 놓친다
     */
    const val KM_EPSILON = 1e-6

    /**
     * 다시 켜졌을 때 달리는 중이어도, 끝나지 않은 운행의 마지막 저장이 이보다 오래됐으면 잇지 않고 마감한 뒤 새로 연다 (분).
     * 어젯밤 주차 저장을 놓친 운행에 오늘 아침 운행이 이어 붙지 않게. 달리는 동안은 계기판이 바뀔 때마다 저장한다
     */
    const val RESUME_MAX_GAP_MINUTES = 30L

    /**
     * 연료·배터리 잔량이 이만큼(%p) 이상 오르면 주유·충전으로 본다.
     * 회생 제동이나 센서 흔들림(1~2%p)은 넘고, 짧은 충전(10분 급속이면 10%p 안팎)은 잡는 값
     */
    const val REFUEL_MIN_RISE_PERCENT = 5

    /** 말한 주유·충전 금액의 상한 (원). 이보다 크면 잘못 알아들은 것으로 본다 */
    const val REFUEL_AMOUNT_MAX_WON = 1_000_000L

    /** 내비 poiId 가 없을 때 같은 장소로 보는 반경 (집·회사, m) */
    const val PLACE_MATCH_RADIUS_M = 300.0

    /** 사용법 안내(토글 아래 기본값 안내, 방금 운행 카드 설명)는 설치 후 이 날수까지만 보여 준다 */
    const val HINT_DAYS = 3

    /** 사업연도 시작 월. 법인은 정관을 따른다 */
    // TODO: 설정에서 고르게 한다 (지금은 1월 시작만)
    const val FISCAL_YEAR_START_MONTH = 1

    /** 첫 실행에서 등록하는 사람 수 (여러 명·기사 차). 1명이면 혼자 쓰는 차와 같다. 설정에서 더할 때도 [PEOPLE_MAX] 까지 */
    const val PEOPLE_MIN = 2
    const val PEOPLE_MAX = 4

    /** 사람 이름·부서 글자 수 상한 (서식 ④ 칸에 들어갈 만큼) */
    const val PERSON_TEXT_MAX = 20

    /** 화면의 "오늘"은 자정을 이만큼 넘겨 다시 읽고, 길어도 [DAY_CHECK_MAX_MILLIS] 마다 다시 본다 (시스템 시계가 바뀌는 경우) */
    const val MIDNIGHT_MARGIN_MILLIS = 1_000L
    const val DAY_CHECK_MAX_MILLIS = 60 * 60 * 1_000L

    /** 방금 운행 카드를 닫은 뒤 "닫았어요 · [다시 보기]" 줄이 남는 시간 */
    const val RECENT_CLOSED_MILLIS = 6_000L

    /** 화면 아래 알림 줄(저장 실패·음성 못 알아들음)이 보이는 시간. "다시 하기"가 붙으면 누를 틈을 더 준다 */
    const val NOTICE_MILLIS = 4_000L
    const val NOTICE_WITH_ACTION_MILLIS = 8_000L

    /** 보여 주기 전 쌓아 둘 알림 수. 넘치면 오래된 것부터 버린다 */
    const val NOTICE_QUEUE_SIZE = 4

    /** 설정에 보여 줄 관리자 수정 기록 수 */
    const val ADMIN_LOG_ROWS = 5

    /** 사업자등록번호: 숫자 10자리, 3·5자리 뒤에 대시 ("123-45-67890") */
    const val BIZ_NO_LENGTH = 10
    val BIZ_NO_DASH_AFTER = setOf(3, 5)

    /** 법인명·차종·번호판 글자 수 상한 (서식 칸에 들어갈 만큼) */
    const val VEHICLE_TEXT_MAX = 30

    /**
     * 내보내기·백업 묶음 형식 버전. 묶음 안 필드를 바꾸면 올린다 (받는 쪽이 옛 묶음도 읽게).
     * 차대번호 해시는 앞에 앱 고유 글자를 붙여 SHA-256 을 하고 앞 [VIN_HASH_HEX_LENGTH] 자리만 쓴다
     */
    const val BUNDLE_FORMAT_VERSION = 1
    const val VIN_HASH_PREFIX = "chagok-vin:"
    const val VIN_HASH_HEX_LENGTH = 32

    /** 관리자 PIN · 복구키 */
    const val PIN_LENGTH = 4
    const val RECOVERY_KEY_LENGTH = 16 // 숫자만. PIN 과 같은 숫자판으로 넣는다
    const val RECOVERY_KEY_GROUP = 4 // 복구키를 보여 줄 때 끊어 읽는 자리 수
    const val PIN_MAX_TRIES = 5 // 이만큼 틀리면 복구키로만 연다
}
