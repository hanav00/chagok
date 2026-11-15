package kr.chagok.domain.model

import kr.chagok.testing.TripFixtures.HOME
import kr.chagok.testing.TripFixtures.OFFICE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 기록 방식·등록 정보 [ChagokSettings] */
class ChagokSettingsTest {
    private val installed = LocalDate.of(2026, 10, 26)

    private fun settingsInstalledOn(date: LocalDate?) = ChagokSettings(installedOn = date)

    @Test
    fun `집과 회사가 모두 있어야 집·회사가 있는 것`() {
        assertTrue(ChagokSettings(home = HOME, office = OFFICE).hasHomeOffice)
        assertFalse(ChagokSettings(home = HOME, office = null).hasHomeOffice)
        assertFalse(ChagokSettings(home = null, office = OFFICE).hasHomeOffice)
        assertFalse(ChagokSettings().hasHomeOffice)
    }

    @Test
    fun `설치한 날(1일째)부터 3일째까지 사용법 안내를 보여 준다`() {
        val settings = settingsInstalledOn(installed)

        assertTrue(settings.isHintPeriod(installed))
        assertTrue(settings.isHintPeriod(installed.plusDays(1)))
        assertTrue(settings.isHintPeriod(installed.plusDays(2)))
    }

    @Test
    fun `4일째부터는 사용법 안내를 숨긴다`() {
        val settings = settingsInstalledOn(installed)

        assertFalse(settings.isHintPeriod(installed.plusDays(3)))
        assertFalse(settings.isHintPeriod(installed.plusDays(30)))
    }

    @Test
    fun `안내 기간은 달이 바뀌어도 날수로 센다 (10월 30일 설치 → 11월 1일이 3일째)`() {
        val settings = settingsInstalledOn(LocalDate.of(2026, 10, 30))

        assertTrue(settings.isHintPeriod(LocalDate.of(2026, 11, 1)))
        assertFalse(settings.isHintPeriod(LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun `설치일을 아직 못 남겼으면 막 설치한 것으로 보고 안내를 보여 준다`() {
        assertTrue(settingsInstalledOn(null).isHintPeriod(installed.plusDays(100)))
    }

    @Test
    fun `처음 설정 기본값은 업무 · 혼자 쓰는 차 · 음성 안내 켬 · 집·회사 없음`() {
        val settings = ChagokSettings()

        assertFalse(settings.isFirstRunDone)
        assertEquals(Category.BUSINESS, settings.defaultCategory)
        assertEquals(VehicleUsage.SOLO, settings.usage)
        assertTrue(settings.isTripVoiceOn)
        assertFalse(settings.hasHomeOffice)
    }
}
