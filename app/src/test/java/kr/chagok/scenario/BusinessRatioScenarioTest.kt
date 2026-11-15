package kr.chagok.scenario

import kotlinx.coroutines.runBlocking
import kr.chagok.domain.FiscalYear
import kr.chagok.domain.TripRules
import kr.chagok.domain.model.Category
import kr.chagok.testing.TestApp
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TripFixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.YearMonth

/**
 * 업무사용비율: 하루를 달리고 나면 ⑪총주행거리는 계기판이 늘어난 거리와 같고,
 * ⑫업무용 사용거리(출퇴근 + 일반 업무) ÷ ⑪ 이 ⑬업무사용비율이 된다. 분류를 고치면 비율도 바로 바뀐다.
 */
@RunWith(RobolectricTestRunner::class)
class BusinessRatioScenarioTest {
    private lateinit var app: TestApp

    @Before
    fun setUp() = runBlocking<Unit> {
        app = TestApp()
        app.startRecording()
        app.completeFirstRun(home = TripFixtures.HOME, office = TripFixtures.OFFICE)

        app.navi.location = TripFixtures.HOME
        app.driveTrip(km = 12.0, destination = TripFixtures.OFFICE)
        app.navi.location = TripFixtures.OFFICE
        app.driveTrip(km = 30.0, destination = TripFixtures.CLIENT)
        app.navi.location = TripFixtures.CLIENT
        app.nextDeparture.pickCategory(Category.PERSONAL)
        await(app.nextDeparture.next) { it?.category == Category.PERSONAL }
        app.driveTrip(km = 10.0, destination = TripFixtures.MART)
        app.driveWhileAppOff(8.0)
        app.navi.location = TripFixtures.MART
        app.driveTrip(km = 20.0, destination = TripFixtures.OFFICE)
    }

    @After
    fun tearDown() = app.close()

    @Test
    fun `총주행거리는 계기판이 늘어난 거리와 같고 분류별 거리가 나뉘어 쌓인다`() {
        val totals = TripRules.totalsOf(app.trips.trips.value)

        assertEquals(app.vehicle.odometerKm.value!! - TestApp.START_ODOMETER_KM, totals.total, 1e-9)
        assertEquals(80.0, totals.total, 1e-9)
        assertEquals(12.0, totals.commute, 1e-9)
        assertEquals(50.0, totals.business, 1e-9)
        assertEquals(10.0, totals.personal, 1e-9)
        assertEquals(8.0, totals.pending, 1e-9)
        assertEquals(62.0 / 80.0, totals.businessRatio, 1e-9)
    }

    @Test
    fun `기록 안 된 주행을 고르고 개인 운행을 고치면 업무사용비율이 바로 바뀐다`() = runBlocking<Unit> {
        val all = app.trips.trips.value
        app.trips.classify(all.single { it.isGap }.id, Category.BUSINESS)
        app.trips.classify(all.single { it.isPersonal }.id, Category.BUSINESS)

        val updated = await(app.trips.trips) { list -> list.none { it.isPending || it.isPersonal } }
        val totals = TripRules.totalsOf(updated)
        assertEquals(80.0, totals.total, 1e-9)
        assertEquals(0.0, totals.pending, 0.0)
        assertEquals(1.0, totals.businessRatio, 1e-9)
    }

    @Test
    fun `이번 달·올해 사업연도로 묶어도 같은 합계가 나온다`() {
        val all = app.trips.trips.value
        val today = TripFixtures.TODAY

        val month = TripRules.totalsOf(TripRules.ofMonth(all, YearMonth.from(today)))
        val year = TripRules.totalsOf(TripRules.ofFiscalYear(all, FiscalYear.of(today)))

        assertEquals(TripRules.totalsOf(all), month)
        assertEquals(TripRules.totalsOf(all), year)
        assertEquals(0, TripRules.ofMonth(all, YearMonth.from(today).minusMonths(1)).size)
    }

    @Test
    fun `오늘 요약은 분류한 운행만 분류별 횟수·거리·도착지로 보여 준다`() {
        val summary = TripRules.daySummary(app.trips.trips.value, TripFixtures.TODAY)!!

        assertEquals(2, summary.getValue(Category.BUSINESS).count)
        assertEquals(50.0, summary.getValue(Category.BUSINESS).km, 1e-9)
        assertEquals(listOf("○○전자", "회사"), summary.getValue(Category.BUSINESS).placeNames)
        assertEquals(1, summary.getValue(Category.COMMUTE).count)
        assertEquals(1, summary.getValue(Category.PERSONAL).count)
        assertEquals(emptyList<String>(), summary.getValue(Category.PERSONAL).placeNames)
    }
}
