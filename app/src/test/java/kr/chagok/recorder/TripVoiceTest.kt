package kr.chagok.recorder

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kr.chagok.domain.PastVisit
import kr.chagok.domain.model.Category
import kr.chagok.testing.RecordingVoiceOutput
import kr.chagok.testing.TripFixtures
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 도착 음성 안내 문장: 이름 있는 목적지면 "○○ 도착", 없으면 "○km 운행", 끝은 분류 단어 (업무로·출퇴근으로·개인으로) */
@RunWith(RobolectricTestRunner::class)
class TripVoiceTest {
    private val output = RecordingVoiceOutput()
    private val voice = TripVoice(ApplicationProvider.getApplicationContext<Application>().resources, output)

    @Test
    fun `이름 있는 내비 목적지로 간 업무 운행 → ○○ 도착, 업무로`() {
        val trip = TripFixtures.trip(category = Category.BUSINESS, destination = TripFixtures.CLIENT)

        assertEquals("○○전자 도착, 업무로 기록했어요.", voice.sentence(trip))
    }

    @Test
    fun `집에서 회사로 간 출퇴근 운행 → 회사 도착, 출퇴근으로`() {
        val trip = TripFixtures.trip(
            category = Category.COMMUTE,
            origin = TripFixtures.HOME,
            destination = TripFixtures.OFFICE,
        )

        assertEquals("회사 도착, 출퇴근으로 기록했어요.", voice.sentence(trip))
    }

    @Test
    fun `내비 없이 주소만 남은 업무 운행 → 거리 운행, 업무로`() {
        val trip = TripFixtures.trip(startKm = 1_000.0, endKm = 1_008.8, destination = TripFixtures.STREET)

        assertEquals("8.8킬로미터 운행, 업무로 기록했어요.", voice.sentence(trip))
    }

    @Test
    fun `도착지가 아예 없는 업무 운행도 거리로 알린다`() {
        val trip = TripFixtures.trip(startKm = 100.0, endKm = 112.0, destination = null)

        assertEquals("12.0킬로미터 운행, 업무로 기록했어요.", voice.sentence(trip))
    }

    @Test
    fun `개인 운행은 장소 없이 거리만 → 개인으로`() {
        val trip = TripFixtures.trip(startKm = 100.0, endKm = 110.0, category = Category.PERSONAL)

        assertEquals("10.0킬로미터 운행, 개인으로 기록했어요.", voice.sentence(trip))
    }

    @Test
    fun `긴 거리는 천 단위 쉼표와 소수 한 자리로 읽는다`() {
        val trip = TripFixtures.trip(startKm = 0.0, endKm = 1_234.56, destination = null)

        assertEquals("1,234.6킬로미터 운행, 업무로 기록했어요.", voice.sentence(trip))
    }

    @Test
    fun `안내하면 그 문장을 한 번 말한다`() {
        val trip = TripFixtures.trip(destination = TripFixtures.CLIENT)

        voice.announceArrival(trip)

        assertEquals(listOf("○○전자 도착, 업무로 기록했어요."), output.spoken)
    }

    @Test
    fun `혼자 탄 차로 메모를 남긴 곳에 다시 가면 지난 메모까지 읽는다`() {
        val visit = PastVisit(lastDate = TripFixtures.TODAY.minusDays(21), note = "신제품 견적 요청")

        assertEquals(
            "○○전자, 3주 만이에요. 지난번 메모, 신제품 견적 요청.",
            voice.visitSentence("○○전자", visit, TripFixtures.TODAY, isAlone = true),
        )
    }

    @Test
    fun `동승자가 있으면 메모는 빼고 얼마 만인지만 말한다`() {
        val visit = PastVisit(lastDate = TripFixtures.TODAY.minusDays(21), note = "신제품 견적 요청")

        assertEquals("○○전자, 3주 만이에요.", voice.visitSentence("○○전자", visit, TripFixtures.TODAY, isAlone = false))
    }

    @Test
    fun `얼마 만인지는 날수가 커질수록 큰 단위로 어림한다`() {
        fun since(days: Long) = voice.visitSentence(
            "○○전자",
            PastVisit(lastDate = TripFixtures.TODAY.minusDays(days), note = "메모"),
            TripFixtures.TODAY,
            isAlone = false,
        )

        assertEquals("○○전자, 오늘 또 가요.", since(0))
        assertEquals("○○전자, 13일 만이에요.", since(13))
        assertEquals("○○전자, 2주 만이에요.", since(14))
        assertEquals("○○전자, 8주 만이에요.", since(59))
        assertEquals("○○전자, 3개월 만이에요.", since(95))
    }
}
