package kr.chagok.ui.table

import kotlinx.coroutines.runBlocking
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.VehicleInfo
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.testing.DEMO_PIN
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TripFixtures.TODAY
import kr.chagok.testing.TripFixtures.trip
import kr.chagok.testing.ViewModelTestRule
import kr.chagok.testing.installedOnDay
import kr.chagok.testing.seedTrips
import kr.chagok.testing.setUpLockedAdmin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.YearMonth

/**
 * 운행기록부 탭: 이전·다음 달 넘기기, 서식 한 줄 = 운행 하나, 이 달 합계(표 아래 줄),
 * ⑪⑫⑬ 사업연도 합계. PIN 으로 잠긴 차는 오늘 운행만 보이고 이 달 합계 줄을 숨긴다.
 * 핵심 칸 / 서식 전체 칸은 화면 크기에 따른 화면 쪽 값이라 ViewModel 에는 없다 (서식 머리 펼침만 여기 있다).
 */
@RunWith(RobolectricTestRunner::class)
class TableViewModelTest {
    @get:Rule
    val rule = ViewModelTestRule()
    private val app get() = rule.app

    private fun table() = rule.viewModel { TableViewModel(app.container) }

    /** 9월 둘, 10월 셋(그중 하나는 오늘), 11월 하나 */
    private suspend fun seedAutumn() = app.seedTrips(
        trip(startKm = 0.0, endKm = 30.0, date = LocalDate.of(2026, 9, 3)),
        trip(startKm = 30.0, endKm = 50.0, date = LocalDate.of(2026, 9, 28), category = Category.PERSONAL),
        trip(startKm = 50.0, endKm = 90.0, date = LocalDate.of(2026, 10, 2)),
        trip(startKm = 90.0, endKm = 100.0, date = LocalDate.of(2026, 10, 15), category = Category.COMMUTE),
        trip(startKm = 100.0, endKm = 105.0, date = TODAY, category = Category.PERSONAL),
        trip(startKm = 105.0, endKm = 125.0, date = LocalDate.of(2026, 11, 1)),
    )

    @Test
    fun `처음 열면 이번 달 → 이번 달 운행만, 이 달 합계`() = runBlocking<Unit> {
        app.completeFirstRun()
        val trips = seedAutumn()

        val state = await(table().state) { it?.rows?.size == 3 }!!
        assertEquals(YearMonth.of(2026, 10), state.month)
        assertEquals(trips.subList(2, 5).map { it.id }, state.rows.map { it.trip.id })
        assertEquals(55.0, state.monthTotals.total, 1e-9)
        assertEquals(40.0, state.monthTotals.business, 1e-9)
        assertEquals(10.0, state.monthTotals.commute, 1e-9)
        assertEquals(5.0, state.monthTotals.personal, 1e-9)
        assertFalse(state.isLocked)
        assertTrue("이번 달이라 화면이 표 끝(최근 줄)으로 내린다", state.isThisMonth)
    }

    @Test
    fun `달을 넘기지 않고 띄워 둔 채 달이 바뀜 → 새 달을 연다`() = runBlocking<Unit> {
        app.completeFirstRun()
        seedAutumn()
        val table = table()
        await(table.state) { it?.month == YearMonth.of(2026, 10) }

        app.passDays(6) // 10월 26일 → 11월 1일

        val november = await(table.state) { it?.month == YearMonth.of(2026, 11) }!!
        assertTrue(november.isThisMonth)
        assertEquals(1, november.rows.size)
    }

    @Test
    fun `지난 달을 보는 중에 달이 바뀜 → 보던 달 그대로, 이번 달로 돌아오면 다시 따라간다`() = runBlocking<Unit> {
        app.completeFirstRun()
        seedAutumn()
        val table = table()
        await(table.state) { it?.month == YearMonth.of(2026, 10) }
        rule.onMain { table.shiftMonth(-1) }
        await(table.state) { it?.month == YearMonth.of(2026, 9) }

        app.passDays(6)
        val after = await(table.state) { it?.isThisMonth == false && it.month == YearMonth.of(2026, 9) }!!
        assertEquals(YearMonth.of(2026, 9), after.month)

        rule.onMain { table.shiftMonth(2) } // 9월 → 11월(이번 달)
        await(table.state) { it?.month == YearMonth.of(2026, 11) && it.isThisMonth }
        app.passDays(30) // 12월 1일
        await(table.state) { it?.month == YearMonth.of(2026, 12) }
    }

    @Test
    fun `이전 달로 → 9월 운행과 9월 합계, 다음 달로 두 번 → 11월`() = runBlocking<Unit> {
        app.completeFirstRun()
        seedAutumn()
        val table = table()
        await(table.state) { it?.rows?.size == 3 }

        rule.onMain { table.shiftMonth(-1) }
        val september = await(table.state) { it?.month == YearMonth.of(2026, 9) }!!
        assertEquals(2, september.rows.size)
        assertEquals(50.0, september.monthTotals.total, 1e-9)
        assertFalse("지난 달은 위에서부터 본다", september.isThisMonth)

        rule.onMain { table.shiftMonth(1) }
        rule.onMain { table.shiftMonth(1) }
        val november = await(table.state) { it?.month == YearMonth.of(2026, 11) }!!
        assertEquals(listOf(20.0), november.rows.map { it.trip.km })
    }

    @Test
    fun `운행이 없는 달 → 빈 표, 합계 0`() = runBlocking<Unit> {
        app.completeFirstRun()
        seedAutumn()
        val table = table()

        rule.onMain { table.shiftMonth(-3) }

        val state = await(table.state) { it?.month == YearMonth.of(2026, 7) }!!
        assertTrue(state.rows.isEmpty())
        assertEquals(0.0, state.monthTotals.total, 0.0)
    }

    @Test
    fun `⑪⑫⑬ 사업연도 합계는 보고 있는 달과 상관없이 올해 전체 (작년 운행은 빠진다)`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.seedTrips(trip(startKm = 0.0, endKm = 999.0, date = LocalDate.of(2025, 12, 31)))
        seedAutumn()
        val table = table()
        rule.onMain { table.shiftMonth(-1) }

        val state = await(table.state) { it?.month == YearMonth.of(2026, 9) }!!
        assertEquals(2026, state.fiscalYear.year)
        assertEquals(125.0, state.yearTotals.total, 1e-9)
        assertEquals(100.0, state.yearTotals.businessUseKm, 1e-9) // 업무 30+40+20 + 출퇴근 10
        assertEquals(0.8, state.yearTotals.businessRatio, 1e-9)
    }

    @Test
    fun `PIN 으로 잠긴 차 → 오늘 운행만 보이고 잠김 표시 (이 달 합계 줄을 숨긴다)`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김민수"))
        app.setUpLockedAdmin()
        val trips = seedAutumn()

        val state = await(table().state) { it?.isLocked == true && it.rows.isNotEmpty() }!!
        assertEquals(listOf(trips[4].id), state.rows.map { it.trip.id })
        assertEquals(TODAY, state.rows.single().trip.date)
    }

    @Test
    fun `잠긴 차는 지난달로 넘겨도 이번 달에 머문다 (오늘 운행만 보이므로)`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.setUpLockedAdmin()
        seedAutumn()
        val table = table()
        await(table.state) { it?.isLocked == true && it.rows.size == 1 }

        rule.onMain { table.shiftMonth(-1) }

        val state = await(table.state) { it?.isLocked == true }!!
        assertEquals(YearMonth.of(2026, 10), state.month)
        assertEquals(1, state.rows.size)
    }

    @Test
    fun `관리자가 PIN 을 풂 → 이 달 운행 전체, 합계 줄도 보인다`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.setUpLockedAdmin()
        seedAutumn()
        val table = table()
        await(table.state) { it?.isLocked == true && it.rows.size == 1 }

        app.container.admin.unlock(DEMO_PIN)

        val state = await(table.state) { it?.isLocked == false }!!
        assertEquals(3, state.rows.size)
        assertEquals(55.0, state.monthTotals.total, 1e-9)
    }

    @Test
    fun `PIN 을 정하지 않은 차 → 잠금 없이 이 달 전체`() = runBlocking<Unit> {
        app.completeFirstRun()
        seedAutumn()

        val state = await(table().state) { it?.rows?.size == 3 }!!
        assertFalse(state.isLocked)
    }

    @Test
    fun `④성명 칸 → 공용차에서 안 고른 운행만 미지정, 혼자 쓰는 차는 미지정이 없다`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김민수"))
        val kim = app.container.people.people.value[1]
        app.seedTrips(
            trip(startKm = 0.0, endKm = 10.0, personId = kim.id),
            trip(startKm = 10.0, endKm = 20.0, personId = null),
        )
        val table = table()

        val shared = await(table.state) { it?.rows?.size == 2 }!!
        assertEquals("김민수", shared.rows[0].person?.name)
        assertFalse(shared.rows[0].isUnassigned)
        assertNull(shared.rows[1].person)
        assertTrue(shared.rows[1].isUnassigned)

        app.container.settings.setUsage(VehicleUsage.SOLO)
        val solo = await(table.state) { it?.rows?.none(TableRow::isUnassigned) == true }!!
        assertEquals(2, solo.rows.size)
    }

    @Test
    fun `서식 머리 → 등록한 차·사업자 정보, 사업연도`() = runBlocking<Unit> {
        app.completeFirstRun()
        val info = VehicleInfo(model = "아이오닉 5", plate = "12가 3456", company = "주식회사 운행", bizNo = "123-45-67890")
        app.container.settings.setVehicleInfo(info)

        val state = await(table().state) { it?.vehicle?.plate == info.plate }!!
        assertEquals(info, state.vehicle)
        assertEquals(LocalDate.of(2026, 1, 1), state.fiscalYear.start)
        assertEquals(LocalDate.of(2026, 12, 31), state.fiscalYear.end)
    }

    @Test
    fun `서식 머리 펼침 → 처음엔 안 건드림(화면 크기로 정함), 접으면 탭을 오가도 접힌 채`() = runBlocking<Unit> {
        app.completeFirstRun()
        val table = table()
        assertNull(table.isFormHeadOpen.value)

        rule.onMain { table.setFormHeadOpen(false) }

        assertEquals(false, table.isFormHeadOpen.value)
    }

    @Test
    fun `설치 3일째 → 표 사용법 안내가 보인다`() = runBlocking<Unit> {
        app.installedOnDay(3)
        app.completeFirstRun()
        assertTrue(await(table().state) { it != null }!!.isHintPeriod)
    }

    @Test
    fun `설치 4일째 → 표 사용법 안내가 없다`() = runBlocking<Unit> {
        app.installedOnDay(4)
        app.completeFirstRun()
        assertFalse(await(table().state) { it != null }!!.isHintPeriod)
    }

    @Test
    fun `운행 분류를 고치면 기록부 합계가 바로 바뀐다`() = runBlocking<Unit> {
        app.completeFirstRun()
        val (trip) = app.seedTrips(trip(startKm = 0.0, endKm = 10.0))
        val table = table()
        await(table.state) { it?.monthTotals?.business == 10.0 }

        app.trips.classify(trip.id, Category.PERSONAL)

        val state = await(table.state) { it?.monthTotals?.personal == 10.0 }!!
        assertEquals(0.0, state.monthTotals.business, 0.0)
        assertEquals(0.0, state.yearTotals.businessRatio, 0.0)
    }
}
