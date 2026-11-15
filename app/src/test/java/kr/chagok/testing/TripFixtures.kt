package kr.chagok.testing

import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.domain.model.Trip
import kr.chagok.domain.model.TripPlace
import java.time.LocalDate
import java.time.LocalTime

/** 테스트에 쓰는 장소·운행 견본. 테스트마다 다른 값만 넘긴다 */
object TripFixtures {
    val TODAY: LocalDate = LocalDate.of(2026, 10, 26)

    val HOME = TripPlace(name = "집", poiId = "home", latitude = 37.366, longitude = 127.108)
    val OFFICE = TripPlace(name = "회사", poiId = "office", latitude = 37.401, longitude = 127.108)
    val CLIENT = TripPlace(name = "○○전자", poiId = "client", latitude = 37.395, longitude = 127.111)

    /** 개인 용무로 가는 곳 */
    val MART = TripPlace(name = "이마트", poiId = "mart", latitude = 37.384, longitude = 127.121)

    /** 내비 없이 운행했을 때 그 자리 주소 (이름·poiId 없음) */
    val STREET = TripPlace(address = "성남시 분당구 판교역로 1", latitude = 37.394, longitude = 127.110)

    fun trip(
        startKm: Double = 100.0,
        endKm: Double = 110.0,
        category: Category? = Category.BUSINESS,
        reason: ClassifyReason? = category?.let(ClassifyReason::defaultOf),
        date: LocalDate = TODAY,
        personId: Long? = 1,
        origin: TripPlace? = OFFICE.takeIf { category != Category.PERSONAL },
        destination: TripPlace? = CLIENT.takeIf { category != Category.PERSONAL },
        note: String = "",
        isGap: Boolean = false,
        id: Long = 0,
    ) = Trip(
        id = id,
        date = date,
        personId = personId,
        origin = origin,
        destination = destination,
        startKm = startKm,
        endKm = endKm,
        startedAt = LocalTime.of(9, 0).takeUnless { isGap },
        endedAt = LocalTime.of(9, 30).takeUnless { isGap },
        category = category,
        reason = reason,
        note = note,
        isGap = isGap,
    )
}
