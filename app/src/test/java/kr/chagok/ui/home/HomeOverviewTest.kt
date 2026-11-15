package kr.chagok.ui.home

import kotlinx.coroutines.runBlocking
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TripFixtures
import kr.chagok.testing.TripFixtures.TODAY
import kr.chagok.testing.TripFixtures.trip
import kr.chagok.testing.ViewModelTestRule
import kr.chagok.testing.installedOnDay
import kr.chagok.testing.seedTrips
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
 * 홈의 볼거리: "다음 출발 [업무|개인]" 토글과 그 아래 한 줄, 공용차 "누가 타나요?", 오늘 요약,
 * 요약 카드(업무사용비율·이번 달·기록 연속성).
 */
@RunWith(RobolectricTestRunner::class)
class HomeOverviewTest {
    @get:Rule
    val rule = ViewModelTestRule()
    private val app get() = rule.app

    private fun home() = rule.viewModel { HomeViewModel(app.container) }

    @Test
    fun `설치 첫날 → 토글은 기본값 업무, 아래 줄에 기본값 안내`() = runBlocking<Unit> {
        app.installedOnDay(1)
        app.completeFirstRun(defaultCategory = Category.BUSINESS)

        val next = await(home().state) { it != null }!!.next
        assertEquals(Category.BUSINESS, next.category)
        assertFalse(next.isChanged)
        assertTrue("사용법 안내 기간에는 기본값 안내가 보인다", next.isNoteShown)
    }

    @Test
    fun `설치 4일째 → 기본값 그대로면 토글 아래 줄이 없다`() = runBlocking<Unit> {
        app.installedOnDay(4)
        app.completeFirstRun()

        val next = await(home().state) { it != null }!!.next
        assertFalse(next.isChanged)
        assertFalse(next.isNoteShown)
    }

    @Test
    fun `설치 4일째라도 개인으로 바꾸면 → 다음 출발만 개인 줄이 늘 보인다, 업무로 되돌리면 사라진다`() = runBlocking<Unit> {
        app.installedOnDay(4)
        app.completeFirstRun(defaultCategory = Category.BUSINESS)
        val home = home()

        rule.onMain { home.pickNextCategory(Category.PERSONAL) }
        val changed = await(home.state) { it?.next?.category == Category.PERSONAL }!!.next
        assertTrue(changed.isChanged)
        assertTrue(changed.isNoteShown)
        assertEquals(Category.BUSINESS, changed.defaultCategory)

        rule.onMain { home.pickNextCategory(Category.BUSINESS) }
        val back = await(home.state) { it?.next?.category == Category.BUSINESS }!!.next
        assertFalse("기본값과 같은 쪽을 고르면 바꾼 것이 아니다", back.isChanged)
        assertFalse(back.isNoteShown)
    }

    @Test
    fun `기본값이 개인인 차에서 업무를 고름 → 바꾼 것으로 보인다`() = runBlocking<Unit> {
        app.installedOnDay(4)
        app.completeFirstRun(defaultCategory = Category.PERSONAL)
        val home = home()

        rule.onMain { home.pickNextCategory(Category.BUSINESS) }

        val next = await(home.state) { it?.next?.category == Category.BUSINESS }!!.next
        assertTrue(next.isChanged)
        assertEquals(Category.PERSONAL, next.defaultCategory)
    }

    @Test
    fun `개인으로 바꾸고 다녀옴 → 다음 출발은 다시 기본값 업무`() = runBlocking<Unit> {
        app.startRecording()
        app.completeFirstRun()
        val home = home()
        rule.onMain { home.pickNextCategory(Category.PERSONAL) }
        await(home.state) { it?.next?.isChanged == true }

        app.driveTrip(km = 5.0)

        val next = await(home.state) { it?.next?.isChanged == false }!!.next
        assertEquals(Category.BUSINESS, next.category)
    }

    @Test
    fun `혼자 쓰는 차 → 누가 타나요 칸이 없다`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SOLO)

        assertNull(await(home().state) { it != null }!!.next.who)
    }

    @Test
    fun `공용차 → 누가 타나요는 비어 있고 지난번 탄 사람을 알려 준다, 고르면 그 사람`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김민수"))
        val (park, kim) = app.container.people.people.value
        app.seedTrips(
            trip(startKm = 100.0, endKm = 110.0, personId = park.id),
            trip(startKm = 110.0, endKm = 120.0, personId = kim.id),
            trip(startKm = 120.0, endKm = 130.0, personId = null), // 미지정은 "지난번"이 아니다
        )
        val home = home()

        val who = await(home.state) { it?.next?.who?.lastPersonId != null }!!.next.who!!
        assertNull(who.selectedId)
        assertEquals(kim.id, who.lastPersonId)

        rule.onMain { home.pickNextPerson(park.id) }
        await(home.state) { it?.next?.who?.selectedId == park.id }
    }

    @Test
    fun `오늘 운행이 없다 → 오늘 요약이 없다`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.seedTrips(trip(date = TODAY.minusDays(1)))

        val state = await(home().state) { it?.summary?.monthTotals?.total == 10.0 }!!
        assertNull(state.today)
    }

    @Test
    fun `오늘 업무 두 번(같은 곳)·개인 한 번 → 분류별 횟수·거리, 간 곳은 한 번만, 분류 대기와 어제는 빠진다`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.seedTrips(
            trip(startKm = 90.0, endKm = 97.0, date = TODAY.minusDays(1)), // 어제
            trip(startKm = 100.0, endKm = 110.0, destination = TripFixtures.CLIENT),
            trip(startKm = 110.0, endKm = 115.5, destination = TripFixtures.CLIENT),
            trip(startKm = 115.5, endKm = 118.5, category = Category.PERSONAL),
            trip(startKm = 118.5, endKm = 120.5, category = null, personId = null, isGap = true), // 분류 대기
        )

        val today = await(home().state) { it?.today != null }!!.today!!
        val business = today.getValue(Category.BUSINESS)
        assertEquals(2, business.count)
        assertEquals(15.5, business.km, 1e-9)
        assertEquals(listOf(TripFixtures.CLIENT.name), business.placeNames)
        val personal = today.getValue(Category.PERSONAL)
        assertEquals(1, personal.count)
        assertEquals(3.0, personal.km, 1e-9)
        assertEquals("개인 운행은 장소가 없다", emptyList<String>(), personal.placeNames)
        assertEquals(0, today.getValue(Category.COMMUTE).count)
    }

    @Test
    fun `오늘 운행 줄 → 분류 대기까지 오늘 것만 시간순, 어제는 빠진다`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.seedTrips(
            trip(startKm = 90.0, endKm = 97.0, date = TODAY.minusDays(1)),
            trip(startKm = 100.0, endKm = 110.0, destination = TripFixtures.CLIENT),
            trip(startKm = 110.0, endKm = 113.0, category = Category.PERSONAL),
            trip(startKm = 113.0, endKm = 115.0, category = null, personId = null, isGap = true),
        )

        val trips = await(home().state) { it?.todayTrips?.size == 3 }!!.todayTrips
        assertEquals(listOf(10.0, 3.0, 2.0), trips.map { it.km })
        assertTrue("분류 대기도 오늘 운행이다", trips.last().isGap)
    }

    @Test
    fun `화면을 띄워 둔 채 자정이 지남 → 어제가 된 운행은 오늘 요약에서 빠진다`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.seedTrips(trip())
        val home = home()
        await(home.state) { it?.today != null }

        app.passDays(1)

        assertNull(await(home.state) { it != null && it.today == null }!!.today)
    }

    @Test
    fun `요약 카드 움직임 → 그날 처음 한 번만, 다음 날 다시 한 번`() = runBlocking<Unit> {
        app.completeFirstRun()
        val home = home()
        await(home.state) { it != null }

        assertTrue(home.takeSummaryIntro())
        assertFalse("같은 날 다시 열면 움직이지 않는다", home.takeSummaryIntro())

        app.passDays(1)
        await(home.state) { it != null }
        assertTrue(home.takeSummaryIntro())
    }

    @Test
    fun `올해 업무 851km·개인 149km → 업무사용비율 85·1퍼센트, 작년 운행은 빠진다`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.seedTrips(
            trip(startKm = 0.0, endKm = 500.0, date = LocalDate.of(2025, 12, 30)), // 지난 사업연도
            trip(startKm = 500.0, endKm = 1_351.0, date = LocalDate.of(2026, 3, 10)),
            trip(startKm = 1_351.0, endKm = 1_500.0, category = Category.PERSONAL),
        )

        val summary = await(home().state) { it?.summary?.yearTotals?.total == 1_000.0 }!!.summary
        assertEquals(2026, summary.fiscalYear)
        assertEquals(0.851, summary.yearTotals.businessRatio, 1e-9)
        assertEquals(851.0, summary.yearTotals.businessUseKm, 1e-9)
    }

    @Test
    fun `이번 달 합계 → 이번 달 운행만 분류별로 (업무·출퇴근·개인·분류 대기)`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.seedTrips(
            trip(startKm = 0.0, endKm = 300.0, date = LocalDate.of(2026, 9, 30)), // 지난달
            trip(startKm = 300.0, endKm = 340.0, date = LocalDate.of(2026, 10, 1)),
            trip(startKm = 340.0, endKm = 360.0, date = LocalDate.of(2026, 10, 2), category = Category.COMMUTE),
            trip(startKm = 360.0, endKm = 370.0, category = Category.PERSONAL),
            trip(startKm = 370.0, endKm = 375.0, category = null, personId = null, isGap = true),
        )

        val summary = await(home().state) { it?.summary?.monthTotals?.total == 75.0 }!!.summary
        assertEquals(YearMonth.of(2026, 10), summary.month)
        assertEquals(40.0, summary.monthTotals.business, 1e-9)
        assertEquals(20.0, summary.monthTotals.commute, 1e-9)
        assertEquals(10.0, summary.monthTotals.personal, 1e-9)
        assertEquals(5.0, summary.monthTotals.pending, 1e-9)
    }

    @Test
    fun `이번 달 블록 → 날짜마다 그날 운행이 차례로 한 칸씩, 기록 안 된 주행도 한 칸, 지난달은 없다`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.seedTrips(
            trip(startKm = 0.0, endKm = 300.0, date = LocalDate.of(2026, 9, 30)), // 지난달
            trip(startKm = 300.0, endKm = 340.0, date = LocalDate.of(2026, 10, 1)),
            trip(startKm = 360.0, endKm = 370.0, category = Category.PERSONAL),
            trip(startKm = 370.0, endKm = 375.0, category = null, personId = null, isGap = true),
        )

        val summary = await(home().state) { it?.summary?.monthTotals?.total == 55.0 }!!.summary
        assertEquals("10월은 31일", 31, summary.monthDays.size)
        assertEquals(TODAY.dayOfMonth, summary.todayOfMonth)
        assertEquals(listOf(40.0), summary.monthDays[0].map { it.km })
        assertTrue(summary.monthDays[1].isEmpty())
        val today = summary.monthDays[TODAY.dayOfMonth - 1]
        assertEquals(listOf(Category.PERSONAL, null), today.map { it.category })
        assertEquals(listOf(false, true), today.map { it.isGap })
    }

    @Test
    fun `계기판이 마지막 기록과 같다 → 연속성 정상 (계기판 값까지 기록됨)`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.seedTrips(trip(startKm = 990.0, endKm = 1_000.0))
        app.vehicle.odometerKm.value = 1_000.0

        val summary = await(home().state) { it?.summary?.monthTotals?.total == 10.0 }!!.summary
        assertEquals(1_000.0, summary.odometerKm!!, 1e-9)
        assertEquals(0.0, summary.unrecordedKm, 0.0)
    }

    @Test
    fun `계기판이 마지막 기록보다 10·5km 앞서 있다 → 연속성 경고 10·5km`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.seedTrips(trip(startKm = 990.0, endKm = 1_000.0))
        val home = home()

        app.vehicle.odometerKm.value = 1_010.5

        val summary = await(home.state) { (it?.summary?.unrecordedKm ?: 0.0) > 0 }!!.summary
        assertEquals(10.5, summary.unrecordedKm, 1e-9)
    }

    @Test
    fun `계기판 오차(0·1km 미만)는 경고하지 않는다`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.seedTrips(trip(startKm = 990.0, endKm = 1_000.0))
        app.vehicle.odometerKm.value = 1_000.05

        val summary = await(home().state) { it?.summary?.odometerKm == 1_000.05 }!!.summary
        assertEquals(0.0, summary.unrecordedKm, 0.0)
    }

    @Test
    fun `기록이 하나도 없음 → 계기판을 읽어도 연속성 줄을 숨긴다 (아무것도 기록하지 않았다)`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.vehicle.odometerKm.value = 1_000.0

        val summary = await(home().state) { it != null }!!.summary
        assertNull(summary.odometerKm)
        assertEquals(0.0, summary.unrecordedKm, 0.0)
    }

    @Test
    fun `계기판을 아직 못 읽음 → 연속성 줄을 숨긴다`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.seedTrips(trip(startKm = 990.0, endKm = 1_000.0))
        app.vehicle.odometerKm.value = null

        val summary = await(home().state) { it?.summary?.monthTotals?.total == 10.0 }!!.summary
        assertNull(summary.odometerKm)
        assertEquals(0.0, summary.unrecordedKm, 0.0)
    }
}
