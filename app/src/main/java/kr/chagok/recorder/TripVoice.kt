package kr.chagok.recorder

import android.content.res.Resources
import androidx.annotation.StringRes
import kr.chagok.R
import kr.chagok.car.VoiceOutput
import kr.chagok.domain.PastVisit
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.EnergyKind
import kr.chagok.domain.model.Trip
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 운행 음성 안내 한 문장. 화면을 보지 않고도 지금 어떻게 기록되는지 알게 한다.
 * 시동, 내비로 집↔회사 경로를 잡을 때, 메모를 남긴 곳으로 다시 갈 때, 도착 때, 주유·충전을 알아챘을 때 말한다.
 * 설정 "음성 안내"는 부르는 쪽이 본다.
 */
class TripVoice(private val resources: Resources, private val voice: VoiceOutput) {

    fun announceIgnition(category: Category) = voice.speak(ignitionSentence(category))

    fun announceCommuteRoute() = voice.speak(resources.getString(R.string.voice_commute_route))

    fun announceArrival(trip: Trip) = voice.speak(sentence(trip))

    /** "주유를 기록했어요. 금액은 화면에서 말해 주세요." */
    fun announceRefuel(kind: EnergyKind) = voice.speak(
        resources.getString(if (kind == EnergyKind.FUEL) R.string.voice_refueled else R.string.voice_charged),
    )

    fun announceVisit(placeName: String, visit: PastVisit, today: LocalDate, isAlone: Boolean) =
        voice.speak(visitSentence(placeName, visit, today, isAlone))

    /** 첫 실행 "이렇게 기록돼요"의 들어 보기: 실제 안내와 같은 문장을 그대로 말한다 */
    fun preview(text: String) = voice.speak(text)

    /** "업무로 기록됩니다." / "개인으로 기록됩니다." */
    fun ignitionSentence(category: Category): String =
        resources.getString(R.string.voice_ignition, resources.getString(categoryWord(category)))

    /** "○○호텔 도착, 업무로 기록했어요." / "8.8킬로미터 운행, 개인으로 기록했어요." */
    fun sentence(trip: Trip): String {
        val category = resources.getString(categoryWord(trip.category))
        val place = trip.destination?.takeIf { it.hasName }?.name
        return if (place != null) {
            resources.getString(R.string.voice_arrived, place, category)
        } else {
            resources.getString(R.string.voice_driven, String.format(Locale.KOREA, "%,.1f", trip.km), category)
        }
    }

    /**
     * "○○상사, 3주 만이에요. 지난번 메모, 신제품 견적 요청." 손님이 탔을 수 있으니 혼자가 아니면 메모는 빼고
     * "○○상사, 3주 만이에요."까지만 말한다
     */
    fun visitSentence(placeName: String, visit: PastVisit, today: LocalDate, isAlone: Boolean): String {
        val since = sinceWords(ChronoUnit.DAYS.between(visit.lastDate, today))
        return if (isAlone) {
            resources.getString(R.string.voice_visit_note, placeName, since, visit.note)
        } else {
            resources.getString(R.string.voice_visit_again, placeName, since)
        }
    }

    private fun sinceWords(days: Long): String = when {
        days < 1 -> resources.getString(R.string.voice_since_today)
        days < DAYS_IN_TWO_WEEKS -> resources.getString(R.string.voice_since_days, days.toInt())
        days < DAYS_IN_TWO_MONTHS -> resources.getString(R.string.voice_since_weeks, (days / DAYS_IN_WEEK).toInt())
        else -> resources.getString(R.string.voice_since_months, (days / DAYS_IN_MONTH).toInt())
    }

    @StringRes
    private fun categoryWord(category: Category?): Int = when (category) {
        Category.COMMUTE -> R.string.voice_as_commute
        Category.PERSONAL -> R.string.voice_as_personal
        Category.BUSINESS, null -> R.string.voice_as_business
    }

    private companion object {
        // "13일 만", "2주 만", "8주 만", "2개월 만": 날수가 커지면 큰 단위로 어림한다
        const val DAYS_IN_WEEK = 7
        const val DAYS_IN_MONTH = 30
        const val DAYS_IN_TWO_WEEKS = 14
        const val DAYS_IN_TWO_MONTHS = 60
    }
}
