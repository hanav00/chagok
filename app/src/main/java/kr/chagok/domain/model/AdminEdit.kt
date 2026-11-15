package kr.chagok.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 관리자가 고친 기록 한 줄. 지우지 않는다.
 * 값은 분류면 [Category] 이름, 사람이면 [Person.id] 를 글자로 담는다. null 은 분류 대기·미지정.
 */
data class AdminEdit(
    val tripId: Long,
    val tripDate: LocalDate,
    val field: Field,
    val fromValue: String?,
    val toValue: String?,
    val editedAt: LocalDateTime,
) {
    enum class Field { CATEGORY, PERSON }
}
