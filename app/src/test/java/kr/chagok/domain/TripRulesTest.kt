package kr.chagok.domain

import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.domain.model.NextDeparture
import kr.chagok.domain.model.TripPlace
import kr.chagok.testing.DemoSampleTrips
import kr.chagok.testing.TripFixtures.CLIENT
import kr.chagok.testing.TripFixtures.HOME
import kr.chagok.testing.TripFixtures.OFFICE
import kr.chagok.testing.TripFixtures.TODAY
import kr.chagok.testing.TripFixtures.trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * 운행 묶음 규칙 [TripRules] (분류, 빠진 거리, 서식 ⑪~⑬).
 * 맨 아래 "시연 샘플" 묶음은 샘플 운행으로 홈 화면 숫자를 앱 규칙에 따라 다시 계산한다.
 */
class TripRulesTest {
    private val delta = 1e-6

    private fun next(category: Category, isChanged: Boolean = false) = NextDeparture(category, isChanged, personId = 1)

    @Test
    fun `기본값 업무로 거래처에 가면 일반 업무, 근거는 기본값 업무`() {
        val (category, reason) = TripRules.classifyAtArrival(next(Category.BUSINESS), OFFICE, CLIENT, HOME, OFFICE)

        assertEquals(Category.BUSINESS, category)
        assertEquals(ClassifyReason.DEFAULT_BUSINESS, reason)
    }

    @Test
    fun `기본값 업무로 집에서 회사에 가면 출퇴근, 근거는 기본값 업무`() {
        val (category, reason) = TripRules.classifyAtArrival(next(Category.BUSINESS), HOME, OFFICE, HOME, OFFICE)

        assertEquals(Category.COMMUTE, category)
        assertEquals(ClassifyReason.DEFAULT_BUSINESS, reason)
    }

    @Test
    fun `회사에서 집으로 가도 출퇴근 (방향 상관없음)`() {
        val (category, _) = TripRules.classifyAtArrival(next(Category.BUSINESS), OFFICE, HOME, HOME, OFFICE)

        assertEquals(Category.COMMUTE, category)
    }

    @Test
    fun `집에서 출발해 거래처로 가면 출퇴근이 아니라 일반 업무`() {
        val (category, _) = TripRules.classifyAtArrival(next(Category.BUSINESS), HOME, CLIENT, HOME, OFFICE)

        assertEquals(Category.BUSINESS, category)
    }

    @Test
    fun `기본값 개인이면 집에서 회사로 가도 개인, 근거는 기본값 개인`() {
        val (category, reason) = TripRules.classifyAtArrival(next(Category.PERSONAL), HOME, OFFICE, HOME, OFFICE)

        assertEquals(Category.PERSONAL, category)
        assertEquals(ClassifyReason.DEFAULT_PERSONAL, reason)
    }

    @Test
    fun `출발 전에 개인으로 바꾸면 개인, 근거는 출발 전 선택`() {
        val (category, reason) =
            TripRules.classifyAtArrival(next(Category.PERSONAL, isChanged = true), HOME, CLIENT, HOME, OFFICE)

        assertEquals(Category.PERSONAL, category)
        assertEquals(ClassifyReason.PRE_PICK, reason)
    }

    @Test
    fun `기본값 개인인 차에서 출발 전에 업무로 바꾸고 집에서 회사로 가면 출퇴근, 근거는 출발 전 선택`() {
        val (category, reason) =
            TripRules.classifyAtArrival(next(Category.BUSINESS, isChanged = true), HOME, OFFICE, HOME, OFFICE)

        assertEquals(Category.COMMUTE, category)
        assertEquals(ClassifyReason.PRE_PICK, reason)
    }

    @Test
    fun `집·회사를 정하지 않았으면 집에서 회사로 가도 일반 업무`() {
        val (category, _) =
            TripRules.classifyAtArrival(next(Category.BUSINESS), HOME, OFFICE, home = null, office = null)

        assertEquals(Category.BUSINESS, category)
    }

    @Test
    fun `집만 정하고 회사가 없으면 일반 업무`() {
        val (category, _) = TripRules.classifyAtArrival(next(Category.BUSINESS), HOME, OFFICE, HOME, office = null)

        assertEquals(Category.BUSINESS, category)
    }

    @Test
    fun `회사만 정하고 집이 없으면 일반 업무`() {
        val (category, _) =
            TripRules.classifyAtArrival(next(Category.BUSINESS), HOME, OFFICE, home = null, office = OFFICE)

        assertEquals(Category.BUSINESS, category)
    }

    @Test
    fun `도착지를 모르면(내비 없이 운행) 일반 업무`() {
        val (category, _) =
            TripRules.classifyAtArrival(next(Category.BUSINESS), HOME, destination = null, home = HOME, office = OFFICE)

        assertEquals(Category.BUSINESS, category)
    }

    @Test
    fun `계기판이 마지막 기록보다 8·2km 앞서 있으면 빠진 거리는 8·2km`() {
        assertEquals(8.2, TripRules.unrecordedKm(lastEndKm = 100.0, odometerKm = 108.2), delta)
    }

    @Test
    fun `계기판이 정확히 0·1km 앞서 있으면 빠진 거리로 센다 (계기판 한 칸)`() {
        assertEquals(0.1, TripRules.unrecordedKm(lastEndKm = 100.0, odometerKm = 100.1), delta)
        assertEquals(0.1, TripRules.unrecordedKm(lastEndKm = 23_418.2, odometerKm = 23_418.3), delta)
    }

    @Test
    fun `0·1km 보다 적게 앞서 있으면 오차로 보고 0`() {
        assertEquals(0.0, TripRules.unrecordedKm(lastEndKm = 100.0, odometerKm = 100.05), 0.0)
        assertEquals(0.0, TripRules.unrecordedKm(lastEndKm = 100.0, odometerKm = 100.09), 0.0)
    }

    @Test
    fun `계기판이 마지막 기록과 같으면 0`() {
        assertEquals(0.0, TripRules.unrecordedKm(lastEndKm = 100.0, odometerKm = 100.0), 0.0)
    }

    @Test
    fun `계기판이 마지막 기록보다 작으면(계기판 초기화·교체) 음수가 아니라 0`() {
        assertEquals(0.0, TripRules.unrecordedKm(lastEndKm = 23_418.2, odometerKm = 12.0), 0.0)
    }

    @Test
    fun `기록이 없거나 계기판을 아직 못 읽었으면 0`() {
        assertEquals(0.0, TripRules.unrecordedKm(lastEndKm = null, odometerKm = 108.2), 0.0)
        assertEquals(0.0, TripRules.unrecordedKm(lastEndKm = 100.0, odometerKm = null), 0.0)
    }

    @Test
    fun `합계는 분류별로 나눠 더하고, 분류 대기는 총주행거리(분모)에만 들어간다`() {
        val trips = listOf(
            trip(0.0, 10.0, Category.COMMUTE),
            trip(10.0, 40.0, Category.BUSINESS),
            trip(40.0, 50.0, Category.PERSONAL),
            trip(50.0, 60.0, category = null, isGap = true, personId = null),
        )

        val totals = TripRules.totalsOf(trips)

        assertEquals(60.0, totals.total, 0.0)
        assertEquals(10.0, totals.commute, 0.0)
        assertEquals(30.0, totals.business, 0.0)
        assertEquals(10.0, totals.personal, 0.0)
        assertEquals(10.0, totals.pending, 0.0)
        assertEquals(40.0, totals.businessUseKm, 0.0)
        assertEquals(40.0 / 60.0, totals.businessRatio, 1e-9)
    }

    @Test
    fun `분류 대기를 업무로 고르면 업무사용비율이 오른다`() {
        val gap = trip(50.0, 60.0, category = null, isGap = true, personId = null)
        val before = listOf(trip(0.0, 50.0, Category.BUSINESS), gap)
        val after =
            listOf(trip(0.0, 50.0, Category.BUSINESS), gap.reclassified(Category.BUSINESS, ClassifyReason.USER_PICK))

        assertEquals(50.0 / 60.0, TripRules.totalsOf(before).businessRatio, 1e-9)
        assertEquals(1.0, TripRules.totalsOf(after).businessRatio, 1e-9)
        assertEquals(0.0, TripRules.totalsOf(after).pending, 0.0)
    }

    @Test
    fun `운행이 없으면 합계는 모두 0, 비율도 0 (0으로 나누지 않음)`() {
        val totals = TripRules.totalsOf(emptyList())

        assertEquals(0.0, totals.total, 0.0)
        assertEquals(0.0, totals.businessRatio, 0.0)
    }

    @Test
    fun `한 달 묶음은 1일과 말일을 넣고, 앞뒤 달과 다른 해 같은 달은 뺀다`() {
        val trips = listOf(
            trip(date = LocalDate.of(2026, 9, 30), id = 1),
            trip(date = LocalDate.of(2026, 10, 1), id = 2),
            trip(date = LocalDate.of(2026, 10, 31), id = 3),
            trip(date = LocalDate.of(2026, 11, 1), id = 4),
            trip(date = LocalDate.of(2025, 10, 15), id = 5),
        )

        val october = TripRules.ofMonth(trips, YearMonth.of(2026, 10))

        assertEquals(listOf(2L, 3L), october.map { it.id })
    }

    @Test
    fun `사업연도가 1월에 시작하면 1월 1일부터 12월 31일까지`() {
        val trips = listOf(
            trip(date = LocalDate.of(2025, 12, 31), id = 1),
            trip(date = LocalDate.of(2026, 1, 1), id = 2),
            trip(date = LocalDate.of(2026, 12, 31), id = 3),
            trip(date = LocalDate.of(2027, 1, 1), id = 4),
        )

        val year = TripRules.ofFiscalYear(trips, FiscalYear(2026, startMonth = 1))

        assertEquals(listOf(2L, 3L), year.map { it.id })
    }

    @Test
    fun `사업연도가 3월에 시작하면 2월 운행은 앞 사업연도에 들어간다`() {
        val trips = listOf(
            trip(date = LocalDate.of(2026, 2, 28), id = 1),
            trip(date = LocalDate.of(2026, 3, 1), id = 2),
            trip(date = LocalDate.of(2027, 2, 28), id = 3),
            trip(date = LocalDate.of(2027, 3, 1), id = 4),
        )

        assertEquals(listOf(2L, 3L), TripRules.ofFiscalYear(trips, FiscalYear(2026, startMonth = 3)).map { it.id })
        assertEquals(listOf(1L), TripRules.ofFiscalYear(trips, FiscalYear(2025, startMonth = 3)).map { it.id })
    }

    @Test
    fun `하루 요약은 분류마다 건수·거리·도착지 이름(같은 곳은 한 번)을 모은다`() {
        val addressOnly = TripPlace(address = "분당구 백현동")
        val trips = listOf(
            trip(0.0, 9.6, Category.COMMUTE, origin = HOME, destination = OFFICE),
            trip(9.6, 13.8, Category.BUSINESS, destination = CLIENT),
            trip(13.8, 18.0, Category.BUSINESS, destination = CLIENT),
            trip(18.0, 20.0, Category.BUSINESS, destination = addressOnly),
            trip(20.0, 23.8, Category.PERSONAL),
        )

        val summary = TripRules.daySummary(trips, TODAY)!!

        val business = summary.getValue(Category.BUSINESS)
        assertEquals(3, business.count)
        assertEquals(10.4, business.km, delta)
        assertEquals(listOf(CLIENT.name), business.placeNames) // 주소만 아는 곳은 이름 목록에 넣지 않는다
        assertEquals(listOf(OFFICE.name), summary.getValue(Category.COMMUTE).placeNames)
        assertEquals(1, summary.getValue(Category.PERSONAL).count)
        assertEquals(emptyList<String>(), summary.getValue(Category.PERSONAL).placeNames)
    }

    @Test
    fun `하루 요약은 운행이 없는 분류도 0건으로 담는다`() {
        val summary = TripRules.daySummary(listOf(trip(category = Category.BUSINESS)), TODAY)!!

        assertEquals(Category.entries.toSet(), summary.keys)
        assertEquals(0, summary.getValue(Category.COMMUTE).count)
        assertEquals(0.0, summary.getValue(Category.PERSONAL).km, 0.0)
    }

    @Test
    fun `하루 요약은 분류한 운행만 센다 (분류 대기·다른 날은 빠짐)`() {
        val trips = listOf(
            trip(0.0, 10.0, Category.BUSINESS),
            trip(10.0, 18.2, category = null, isGap = true, personId = null),
            trip(18.2, 30.0, Category.BUSINESS, date = TODAY.minusDays(1)),
        )

        val business = TripRules.daySummary(trips, TODAY)!!.getValue(Category.BUSINESS)

        assertEquals(1, business.count)
        assertEquals(10.0, business.km, delta)
    }

    @Test
    fun `그날 운행이 없거나 분류 대기만 있으면 하루 요약은 null`() {
        val gapOnly = listOf(trip(category = null, isGap = true, personId = null))
        val yesterday = listOf(trip(date = TODAY.minusDays(1)))

        assertNull(TripRules.daySummary(emptyList(), TODAY))
        assertNull(TripRules.daySummary(gapOnly, TODAY))
        assertNull(TripRules.daySummary(yesterday, TODAY))
    }

    @Test
    fun `처음 간 곳은 앞 운행에 같은 도착지가 없을 때만`() {
        val trips = listOf(
            trip(0.0, 10.0, id = 1, destination = CLIENT),
            trip(10.0, 20.0, id = 2, destination = OFFICE),
            trip(20.0, 30.0, id = 3, destination = CLIENT),
        )

        assertTrue(TripRules.isFirstVisit(trips[0], trips))
        assertTrue(TripRules.isFirstVisit(trips[1], trips))
        assertFalse(TripRules.isFirstVisit(trips[2], trips))
    }

    @Test
    fun `같은 곳인지는 PlaceMatcher 로 본다 (poiId 가 없어도 300m 안이면 같은 곳)`() {
        val clientByCoords = TripPlace(name = "○○전자 정문", latitude = CLIENT.latitude, longitude = CLIENT.longitude)
        val trips = listOf(
            trip(0.0, 10.0, id = 1, destination = CLIENT),
            trip(10.0, 20.0, id = 2, destination = clientByCoords),
        )

        assertFalse(TripRules.isFirstVisit(trips[1], trips))
    }

    @Test
    fun `이름 없는 도착지(주소만 앎)나 도착지 없는 운행은 처음 간 곳이 아니다`() {
        val addressOnly = trip(id = 1, destination = TripPlace(address = "분당구 백현동"))
        val personal = trip(id = 2, category = Category.PERSONAL)

        assertFalse(TripRules.isFirstVisit(addressOnly, listOf(addressOnly)))
        assertFalse(TripRules.isFirstVisit(personal, listOf(addressOnly, personal)))
    }

    @Test
    fun `지난번 사람은 사람이 정해진 마지막 운행의 사람 (미지정·빠진 주행은 건너뜀)`() {
        val trips = listOf(
            trip(id = 1, personId = 1),
            trip(id = 2, personId = 2),
            trip(id = 3, personId = null),
            trip(id = 4, category = null, isGap = true, personId = null),
        )

        assertEquals(2L, TripRules.lastPersonId(trips))
    }

    @Test
    fun `사람이 정해진 운행이 없으면 지난번 사람은 null`() {
        assertNull(TripRules.lastPersonId(emptyList()))
        assertNull(TripRules.lastPersonId(listOf(trip(personId = null))))
    }

    // 시연 샘플 운행으로 다시 계산한 홈 화면 숫자

    @Test
    fun `시연 샘플 10월 총주행거리는 539·6km (출퇴근 134·4 · 일반 업무 354·6 · 개인 50·6)`() {
        val october = TripRules.totalsOf(TripRules.ofMonth(DemoSampleTrips.trips(), DemoSampleTrips.MONTH))

        assertEquals(539.6, october.total, delta)
        assertEquals(134.4, october.commute, delta)
        assertEquals(354.6, october.business, delta)
        assertEquals(50.6, october.personal, delta)
        assertEquals(0.0, october.pending, 0.0)
    }

    @Test
    fun `시연 샘플 사업연도 ⑪총주행 18,619·6km, ⑫업무용 15,839·0km, ⑬업무사용비율 85·1퍼센트`() {
        val trips = DemoSampleTrips.trips()

        val year = TripRules.totalsOf(TripRules.ofFiscalYear(trips, FiscalYear.of(DemoSampleTrips.TODAY)))

        assertEquals(18_619.6, year.total, delta)
        assertEquals(15_839.0, year.businessUseKm, delta)
        assertEquals(15_839.0 / 18_619.6, year.businessRatio, 1e-9)
        assertEquals(85.1, Math.round(year.businessRatio * 1_000) / 10.0, 0.0) // 화면은 소수 첫째 자리까지
        assertEquals(trips.size, TripRules.ofFiscalYear(trips, FiscalYear.of(DemoSampleTrips.TODAY)).size)
    }

    @Test
    fun `시연 샘플 계기판은 끊기지 않고 이어져 마지막 도착이 23,418·2km`() {
        val trips = DemoSampleTrips.trips()

        trips.zipWithNext().forEach { (before, after) -> assertEquals(before.endKm, after.startKm, 0.0) }
        assertEquals(DemoSampleTrips.END_ODOMETER_KM, trips.last().endKm, delta)
        assertEquals(0.0, TripRules.unrecordedKm(trips.last().endKm, DemoSampleTrips.END_ODOMETER_KM), 0.0)
    }

    @Test
    fun `날짜별 합계 → 그달 날 수만큼, 빈 날은 0, 다른 달 운행은 빠진다`() {
        val trips = listOf(
            trip(startKm = 0.0, endKm = 10.0, date = LocalDate.of(2026, 10, 1)),
            trip(startKm = 10.0, endKm = 14.0, date = LocalDate.of(2026, 10, 1), category = Category.PERSONAL),
            trip(startKm = 14.0, endKm = 20.0, date = LocalDate.of(2026, 10, 31)),
            trip(startKm = 20.0, endKm = 30.0, date = LocalDate.of(2026, 11, 1)),
        )

        val days = TripRules.dailyTotals(trips, YearMonth.of(2026, 10))

        assertEquals(31, days.size)
        assertEquals(10.0, days[0].business, 1e-9)
        assertEquals(4.0, days[0].personal, 1e-9)
        assertEquals(0.0, days[1].total, 0.0)
        assertEquals(6.0, days[30].total, 1e-9)
    }

    @Test
    fun `달별 합계 → 사업연도 시작 월부터 12개, 사업연도 밖 운행은 빠진다`() {
        val fiscalYear = FiscalYear(2026, startMonth = 4)
        val trips = listOf(
            trip(startKm = 0.0, endKm = 5.0, date = LocalDate.of(2026, 3, 31)),
            trip(startKm = 5.0, endKm = 15.0, date = LocalDate.of(2026, 4, 1)),
            trip(startKm = 15.0, endKm = 18.0, date = LocalDate.of(2027, 3, 31), category = Category.COMMUTE),
        )

        val months = TripRules.monthlyTotals(trips, fiscalYear)

        assertEquals(12, months.size)
        assertEquals(10.0, months.first().total, 1e-9)
        assertEquals(3.0, months.last().commute, 1e-9)
        assertEquals(13.0, months.sumOf { it.total }, 1e-9)
    }
}
