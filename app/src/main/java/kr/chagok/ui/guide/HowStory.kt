package kr.chagok.ui.guide

import kr.chagok.domain.model.Category
import kr.chagok.domain.model.Trip
import kr.chagok.domain.model.TripPlace
import kr.chagok.recorder.TripVoice
import java.time.LocalDate

/**
 * "이렇게 기록돼요" 견본 운행. 첫 실행 알아보기와 설정 "사용법 다시 보기"가 같이 쓴다.
 * 기본 분류로 출발하고, Gleo 가 있는 차는 달리는 동안 말로 반대쪽으로 바꿔 그 분류로 도착한다 (연습과 같은 쪽).
 *
 * @property ignition 시동 안내. 실제 음성 안내 문장이다
 * @property arrival 도착 안내 ([endCategory] 로)
 * @property place 견본 도착지 이름
 */
data class HowStory(
    val defaultCategory: Category = Category.BUSINESS,
    val hasGleo: Boolean = false,
    val ignition: String = "",
    val arrival: String = "",
    val place: String = "",
) {
    val endCategory: Category get() = endCategoryOf(defaultCategory, hasGleo)

    companion object {
        const val SAMPLE_KM = 8.8

        fun build(voice: TripVoice, today: LocalDate, defaultCategory: Category, hasGleo: Boolean, place: String) =
            HowStory(
                defaultCategory = defaultCategory,
                hasGleo = hasGleo,
                ignition = voice.ignitionSentence(defaultCategory),
                arrival = voice.sentence(sampleTrip(today, endCategoryOf(defaultCategory, hasGleo), place)),
                place = place,
            )

        private fun endCategoryOf(defaultCategory: Category, hasGleo: Boolean): Category =
            if (hasGleo) ChangePractice.targetFor(defaultCategory) else defaultCategory

        // 개인 운행은 위치가 없어 거리로 말한다 (TripVoice.sentence)
        private fun sampleTrip(today: LocalDate, category: Category, place: String) = Trip(
            date = today,
            personId = null,
            origin = null,
            destination = if (category == Category.PERSONAL) null else TripPlace(name = place),
            startKm = 0.0,
            endKm = SAMPLE_KM,
            startedAt = null,
            endedAt = null,
            category = category,
            reason = null,
        )
    }
}
