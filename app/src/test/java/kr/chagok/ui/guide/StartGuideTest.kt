package kr.chagok.ui.guide

import kr.chagok.domain.model.ChagokSettings
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.testing.TripFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 홈 시작 가이드: 할 일은 실제 상태로 체크되고, 닫았거나 다 하면 없다 */
class StartGuideTest {
    private val firstRunDone = ChagokSettings(isFirstRunDone = true)

    @Test
    fun `혼자 쓰는 차는 PIN 없이 네 가지, 첫 실행만 마쳤으면 1개 됨`() {
        val guide = StartGuide.of(firstRunDone, hasPin = false, hasTrip = false, hasGleo = true)!!

        assertEquals(
            listOf(GuideItem.RECORDING, GuideItem.PLACES, GuideItem.CHANGE, GuideItem.FIRST_TRIP),
            guide.rows.map { it.item },
        )
        assertEquals(1, guide.doneCount)
    }

    @Test
    fun `공용차는 관리자 PIN 도 할 일이다`() {
        val shared = firstRunDone.copy(usage = VehicleUsage.SHARED)

        val guide = StartGuide.of(shared, hasPin = true, hasTrip = false, hasGleo = false)!!

        assertEquals(GuideItem.ADMIN_PIN, guide.rows[1].item)
        assertEquals(2, guide.doneCount)
    }

    @Test
    fun `집·회사 · 말해 보기 · 첫 운행이 실제 상태로 체크된다`() {
        val settings = firstRunDone.copy(home = TripFixtures.HOME, office = TripFixtures.OFFICE, isChangeTried = true)

        val guide = StartGuide.of(settings, hasPin = false, hasTrip = false, hasGleo = true)!!

        assertEquals(listOf(true, true, true, false), guide.rows.map { it.isDone })
    }

    @Test
    fun `다 하거나 닫으면 카드가 없다`() {
        val allDone = firstRunDone.copy(home = TripFixtures.HOME, office = TripFixtures.OFFICE, isChangeTried = true)

        assertNull(StartGuide.of(allDone, hasPin = false, hasTrip = true, hasGleo = true))
        assertNull(
            StartGuide.of(firstRunDone.copy(isGuideDismissed = true), hasPin = false, hasTrip = false, hasGleo = true),
        )
    }
}
