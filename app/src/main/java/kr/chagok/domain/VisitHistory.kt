package kr.chagok.domain

import kr.chagok.domain.model.Trip
import kr.chagok.domain.model.TripPlace
import java.time.LocalDate

/**
 * 같은 곳에 다시 갈 때 들려줄 지난 방문. [note] 는 그곳에 남긴 마지막 방문 목적(⑩비고)이고,
 * [lastDate] 는 메모가 없는 방문까지 쳐서 마지막으로 간 날이다.
 */
data class PastVisit(val lastDate: LocalDate, val note: String)

object VisitHistory {
    /** [trips] 는 저장 순서(id 순). 그곳에 메모를 남긴 적이 없으면 null (말할 것이 없다) */
    fun lastNoted(trips: List<Trip>, place: TripPlace): PastVisit? {
        val visits = trips.filter { !it.isGap && !it.isPersonal && PlaceMatcher.isSamePlace(it.destination, place) }
        val noted = visits.lastOrNull { it.note.isNotBlank() } ?: return null
        return PastVisit(lastDate = visits.maxOf { it.date }, note = noted.note.trim())
    }
}
