package kr.chagok.ui.admin

/** 관리자 PIN 시트를 여는 이유 */
enum class PinMode {
    /** PIN 넣기. "PIN을 잊었어요"로 복구키를 넣으면 새 PIN 을 정한다 (복구키는 그대로) */
    UNLOCK,

    /** 처음 정하기: 새 PIN 과 새 복구키 */
    SETUP,

    /** 새 PIN 만 (복구키는 그대로) */
    CHANGE,

    /** 넘기기: 새 PIN 과 새 복구키. 예전 PIN·복구키는 더 쓸 수 없다 */
    HANDOVER,
}
