package kr.chagok.car

/** 차량 서비스(Pleos SDK·AAOS Car API) 연결 숫자 */
internal object CarConfig {
    /** Vehicle·NaviHelper·Car 서비스 연결은 비동기라, 이만큼 기다렸다가 등록하고 실패하면 다시 등록한다 */
    const val REGISTER_DELAY_MILLIS = 1_500L
    const val REGISTER_MAX_ATTEMPTS = 5

    /** 출발 순간에 계기판 값이 아직 없으면 이만큼 기다린다. 넘으면 그 출발은 기록하지 못한다 */
    const val ODOMETER_WAIT_MILLIS = 5_000L

    /** 현재 위치 요청의 응답을 기다리는 시간. 넘으면 마지막으로 받은 위치를 쓴다 */
    const val LOCATION_TIMEOUT_MILLIS = 2_000L

    /**
     * 이번 요청의 답만 쓸 때(주차한 자리) 기다리는 시간. 낡은 위치로 대신할 수 없어 조금 더 기다린다.
     * 에뮬레이터 첫 응답이 1.4초 걸렸다. 넘으면 도착지를 비운다 (도착 음성도 그만큼 늦어진다)
     */
    const val FRESH_LOCATION_TIMEOUT_MILLIS = 4_000L

    /** 내비 즐겨찾기 다시 읽기를 기다리는 시간 (설정 화면에서 누르고 기다린다) */
    const val BOOKMARKS_TIMEOUT_MILLIS = 3_000L

    const val VIN_LENGTH = 17

    /** STT 준비와 한마디 듣기를 각각 이만큼까지 기다린다 */
    const val LISTEN_TIMEOUT_MILLIS = 10_000L
}
