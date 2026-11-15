package kr.chagok.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ChagokSettings
import kr.chagok.domain.model.NextDeparture
import kr.chagok.domain.model.Person

/**
 * 홈 "다음 출발 [업무|개인]" 토글과 "누가 타나요?"에서 고른 값 (메모리에만 둔다). 고른 값이 없으면 기본값이고,
 * 운행이 끝나면 비워서 다음 운행은 다시 기본값·미지정으로 시작한다.
 * 시동을 알 수 있는 신호가 부팅뿐이라 시동 단위가 아니라 운행마다 비운다.
 * TODO: 실차에서 시동 OFF 가 재부팅인지 대기인지 확인한 뒤 시동 단위로 바꿀지 정한다
 */
class NextDepartureStore(
    settings: Flow<ChagokSettings>,
    people: Flow<List<Person>>,
    scope: CoroutineScope,
) {
    private val picks = MutableStateFlow(Picks())

    /** 설정을 읽기 전에는 null */
    val next: StateFlow<NextDeparture?> = combine(settings, people, picks) { current, available, picked ->
        NextDeparture.resolve(current.defaultCategory, current.usage, available, picked.category, picked.personId)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** 출발 전에 직접 고른 분류. 안 골랐으면 null (기본값이 쓰인다) */
    val pickedCategory: StateFlow<Category?> = picks
        .map { it.category }
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun pickCategory(category: Category) = picks.update { it.copy(category = category) }

    fun pickPerson(personId: Long) = picks.update { it.copy(personId = personId) }

    /** 운행이 끝나면: 다음 운행은 다시 기본값, 공용차는 사람도 비운다 */
    fun clear() {
        picks.value = Picks()
    }

    private data class Picks(val category: Category? = null, val personId: Long? = null)
}
