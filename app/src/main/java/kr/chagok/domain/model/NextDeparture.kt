package kr.chagok.domain.model

/**
 * 다음에 출발할 운행의 분류와 사람 (홈 "다음 출발" 토글 · "누가 타나요?").
 * 출발하는 순간의 값으로 운행을 기록하고, 주행 중에는 바꿀 수 없다.
 *
 * @property isChanged 출발 전에 기본값에서 바꿨는지 (분류 근거 "출발 전 선택")
 */
data class NextDeparture(
    val category: Category,
    val isChanged: Boolean,
    val personId: Long?,
) {
    companion object {
        /**
         * 기본값과 출발 전에 고른 값으로 다음 출발을 정한다. 기본값과 같은 쪽을 고르면 바꾼 것이 아니다.
         * 혼자 쓰는 차는 늘 기본 사용자(첫 사람), 공용차는 고른 사람(안 고르면 미지정)이고,
         * 고른 사람이 그사이 지워지거나 숨겨져 [people] 에 없으면 안 고른 것으로 본다.
         */
        fun resolve(
            defaultCategory: Category,
            usage: VehicleUsage,
            people: List<Person>,
            pickedCategory: Category?,
            pickedPersonId: Long?,
        ): NextDeparture {
            val category = pickedCategory ?: defaultCategory
            val picked = pickedPersonId?.takeIf { id -> people.any { it.id == id } }
            val personId = if (usage.isShared) picked else people.firstOrNull()?.id
            return NextDeparture(category, isChanged = category != defaultCategory, personId = personId)
        }
    }
}
