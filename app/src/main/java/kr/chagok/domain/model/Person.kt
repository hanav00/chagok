package kr.chagok.domain.model

/**
 * 이 차를 쓰는 사람 (서식 ④사용자). 운전자가 아니라 차를 쓴 사람이다. 등록한 순서대로 두고 첫 사람이 기본 사용자다.
 *
 * @property name 이름 없이 등록한 사람은 빈칸. 화면은 "나"·"이름 없음 2", 기록부 ④는 "이름 입력 필요"로 보인다
 * @property initial 얼굴 원 글자. 이름 없이 등록한 사람은 번호("2"), 이 화면을 쓰는 분은 "나"
 * @property isHidden 지난 운행에 쓰여 지우지 않고 숨긴 사람. 고르는 목록·기본 사용자에서 빠진다
 */
data class Person(
    val id: Long,
    val name: String,
    val dept: String = "",
    val initial: String,
    val isHidden: Boolean = false,
) {
    val isNamed: Boolean get() = name.isNotBlank()

    companion object {
        /** 얼굴 원 글자: 이름 첫 글자. 이름이 없으면 [unnamed] (등록 번호나 "나") */
        fun initialOf(name: String, unnamed: String): String = name.trim().take(1).ifEmpty { unnamed }
    }
}
