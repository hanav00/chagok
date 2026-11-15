package kr.chagok.ui.export

import kotlinx.coroutines.runBlocking
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.VehicleInfo
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TripFixtures.trip
import kr.chagok.testing.ViewModelTestRule
import kr.chagok.testing.seedTrips
import kr.chagok.testing.setUpLockedAdmin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.YearMonth

/**
 * 기록부 > 내보내기. 분류 대기·미지정이나 이름 없는 사람(④성명 "이름 입력 필요")이 이 달에 있으면 경고하고 해당 시트를 연다.
 * 올해 합계(명세서용)는 ⑦총주행거리 · ⑧출퇴근 · ⑨일반 업무(⑪⑫⑬ 의 재료)와 아직 분류 안 한 거리다.
 */
@RunWith(RobolectricTestRunner::class)
class ExportViewModelTest {
    @get:Rule
    val rule = ViewModelTestRule()
    private val app get() = rule.app

    private fun export() = rule.viewModel { ExportViewModel(app.container) }

    private suspend fun sharedCar() = app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김민수"))

    @Test
    fun `처음 열면 → 이 달 기록부, 이번 달`() = runBlocking<Unit> {
        app.completeFirstRun()

        val state = await(export().state) { it != null }!!
        assertEquals(ExportMode.MONTH, state.mode)
        assertEquals(YearMonth.of(2026, 10), state.monthExport.month)
    }

    @Test
    fun `띄워 둔 채 해가 바뀜 → 올해 합계는 새 사업연도`() = runBlocking<Unit> {
        app.completeFirstRun()
        val export = export()
        await(export.state) { it?.yearExport?.fiscalYear?.year == 2026 }

        app.passDays(67) // 10월 26일 → 2027년 1월 1일

        await(export.state) { it?.yearExport?.fiscalYear?.year == 2027 }
    }

    @Test
    fun `기록부에서 9월을 보다 내보내기 → 9월 운행 수와 합계`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.seedTrips(
            trip(startKm = 0.0, endKm = 30.0, date = LocalDate.of(2026, 9, 3)),
            trip(startKm = 30.0, endKm = 50.0, date = LocalDate.of(2026, 9, 28), category = Category.PERSONAL),
            trip(startKm = 50.0, endKm = 90.0, date = LocalDate.of(2026, 10, 2)),
        )
        val export = export()

        rule.onMain { export.show(YearMonth.of(2026, 9)) }

        val month = await(export.state) { it?.monthExport?.month == YearMonth.of(2026, 9) }!!.monthExport
        assertEquals(2, month.tripCount)
        assertEquals(50.0, month.totals.total, 1e-9)
        assertEquals(30.0, month.totals.business, 1e-9)
        assertEquals(20.0, month.totals.personal, 1e-9)
    }

    @Test
    fun `공용차 이 달에 분류 대기 둘·미지정 둘 → 수와 먼저 열 운행`() = runBlocking<Unit> {
        sharedCar()
        val trips = app.seedTrips(
            trip(startKm = 0.0, endKm = 10.0, personId = 1),
            trip(startKm = 10.0, endKm = 15.0, category = null, personId = null, isGap = true),
            trip(startKm = 15.0, endKm = 25.0, personId = null),
            trip(startKm = 25.0, endKm = 30.0, category = null, personId = null, isGap = true),
            trip(startKm = 30.0, endKm = 40.0, personId = null),
        )

        val month = await(export().state) { it?.monthExport?.tripCount == 5 }!!.monthExport
        assertEquals(2, month.pendingCount)
        assertEquals(trips[1].id, month.firstPendingId)
        assertEquals("기록 안 된 주행은 미지정으로 세지 않는다", 2, month.unassignedCount)
        assertEquals(trips[2].id, month.firstUnassignedId)
    }

    @Test
    fun `혼자 쓰는 차 → 사람이 비어 있어도 미지정 경고가 없다`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SOLO)
        app.seedTrips(trip(personId = null))

        val month = await(export().state) { it?.monthExport?.tripCount == 1 }!!.monthExport
        assertEquals(0, month.unassignedCount)
        assertNull(month.firstUnassignedId)
    }

    @Test
    fun `다 분류하고 다 고른 달 → 경고 없음`() = runBlocking<Unit> {
        sharedCar()
        app.seedTrips(trip(personId = 1))

        val month = await(export().state) { it?.monthExport?.tripCount == 1 }!!.monthExport
        assertEquals(0, month.pendingCount)
        assertNull(month.firstPendingId)
        assertEquals(0, month.unassignedCount)
    }

    @Test
    fun `분류 대기를 고르면 → 경고 수가 줄고 다음 대기 운행을 연다`() = runBlocking<Unit> {
        app.completeFirstRun()
        val (first, second) = app.seedTrips(
            trip(startKm = 0.0, endKm = 5.0, category = null, personId = null, isGap = true),
            trip(startKm = 5.0, endKm = 9.0, category = null, personId = null, isGap = true),
        )
        val export = export()
        await(export.state) { it?.monthExport?.pendingCount == 2 }

        app.trips.classify(first.id, Category.BUSINESS)

        val month = await(export.state) { it?.monthExport?.pendingCount == 1 }!!.monthExport
        assertEquals(second.id, month.firstPendingId)
    }

    @Test
    fun `올해 합계 → ⑦총주행 ⑧출퇴근 ⑨일반 업무, 분류 대기 거리, 작년 운행은 빠진다`() = runBlocking<Unit> {
        app.completeFirstRun()
        val trips = app.seedTrips(
            trip(startKm = 0.0, endKm = 999.0, date = LocalDate.of(2025, 12, 31)), // 지난 사업연도
            trip(startKm = 999.0, endKm = 1_099.0, date = LocalDate.of(2026, 2, 1), category = Category.COMMUTE),
            trip(startKm = 1_099.0, endKm = 1_699.0, date = LocalDate.of(2026, 5, 1)),
            trip(startKm = 1_699.0, endKm = 1_899.0, date = LocalDate.of(2026, 8, 1), category = Category.PERSONAL),
            trip(
                startKm = 1_899.0,
                endKm = 1_911.5,
                date = LocalDate.of(2026, 9, 1),
                category = null,
                personId = null,
                isGap = true,
            ),
        )
        val export = export()

        rule.onMain { export.selectMode(ExportMode.YEAR) }

        val state = await(export.state) { it?.mode == ExportMode.YEAR }!!
        val year = state.yearExport
        assertEquals(2026, year.fiscalYear.year)
        assertEquals(912.5, year.totals.total, 1e-9) // ⑦
        assertEquals(100.0, year.totals.commute, 1e-9) // ⑧
        assertEquals(600.0, year.totals.business, 1e-9) // ⑨
        assertEquals(700.0, year.totals.businessUseKm, 1e-9) // ⑫
        assertEquals(12.5, year.totals.pending, 1e-9)
        assertEquals(trips[4].id, year.firstPendingId)
    }

    @Test
    fun `올해 합계 → 서식 머리 차량·사업자 정보가 같이 나간다`() = runBlocking<Unit> {
        app.completeFirstRun()
        val info = VehicleInfo(
            vin = "KMHXX00XXXX000000",
            model = "아이오닉 5",
            plate = "12가 3456",
            company = "주식회사 운행",
            bizNo = "123-45-67890",
        )
        app.container.settings.setVehicleInfo(info)
        app.container.settings.setVin(info.vin)

        val year = await(export().state) { it?.yearExport?.vehicle?.vin == info.vin }!!.yearExport
        assertEquals(info, year.vehicle)
    }

    @Test
    fun `잠긴 차라도 내보내기 화면은 운행을 거르지 않는다 (PIN 을 받은 뒤에만 열리므로)`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.setUpLockedAdmin()
        app.seedTrips(
            trip(startKm = 0.0, endKm = 10.0, date = LocalDate.of(2026, 10, 1)),
            trip(startKm = 10.0, endKm = 20.0),
        )

        assertEquals(2, await(export().state) { it?.monthExport?.tripCount == 2 }!!.monthExport.tripCount)
    }

    @Test
    fun `종류를 바꿔도 → 이 달 값은 그대로 들고 있다`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.seedTrips(trip())
        val export = export()
        await(export.state) { it?.monthExport?.tripCount == 1 }

        rule.onMain { export.selectMode(ExportMode.YEAR) }
        await(export.state) { it?.mode == ExportMode.YEAR }
        rule.onMain { export.selectMode(ExportMode.MONTH) }

        val state = await(export.state) { it?.mode == ExportMode.MONTH }!!
        assertEquals(1, state.monthExport.tripCount)
    }

    @Test
    fun `이 달 운행에 이름 없는 사람이 나오면 → 등록 순서대로 알려 주고, 이 달에 안 나온 사람은 빼고 본다`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김민수", "이서연"))
        val (park, kim, lee) = app.people.people.value
        app.people.update(lee.id, "", "")
        app.people.update(kim.id, "", "")
        app.people.update(park.id, "", "")
        await(app.people.people) { list -> list.none { it.isNamed } }
        app.seedTrips(
            trip(startKm = 0.0, endKm = 10.0, personId = lee.id),
            trip(startKm = 10.0, endKm = 20.0, personId = kim.id),
            trip(startKm = 20.0, endKm = 30.0, personId = park.id, date = LocalDate.of(2026, 9, 3)),
        )

        val month = await(export().state) { it?.monthExport?.tripCount == 2 }!!.monthExport
        assertEquals("9월에만 나온 박정훈은 빼고, 등록 순서대로", listOf(kim.id, lee.id), month.unnamedPeople.map { it.id })
    }

    @Test
    fun `이름을 넣으면 → 경고에서 빠진다`() = runBlocking<Unit> {
        sharedCar()
        val kim = app.people.people.value[1]
        app.people.update(kim.id, "", "")
        app.seedTrips(trip(personId = kim.id))
        val export = export()
        await(export.state) { it?.monthExport?.unnamedPeople?.size == 1 }

        app.people.update(kim.id, "김민수", "")

        await(export.state) { it?.monthExport?.unnamedPeople?.isEmpty() == true }
    }

    @Test
    fun `숨긴 사람이라도 이 달 운행에 나오면 이름 없음을 알린다 (기록부에는 나가므로)`() = runBlocking<Unit> {
        sharedCar()
        val kim = app.people.people.value[1]
        app.people.update(kim.id, "", "")
        app.seedTrips(trip(personId = kim.id))
        app.people.remove(kim.id)
        await(app.people.people) { it.size == 1 }

        val month = await(export().state) { it?.monthExport?.tripCount == 1 }!!.monthExport
        assertEquals(listOf(kim.id), month.unnamedPeople.map { it.id })
        assertTrue(month.unnamedPeople.single().isHidden)
    }

    @Test
    fun `이름이 다 있으면 → 이름 없음 경고가 없다`() = runBlocking<Unit> {
        sharedCar()
        app.seedTrips(trip(personId = app.people.people.value[0].id))

        val month = await(export().state) { it?.monthExport?.tripCount == 1 }!!.monthExport
        assertEquals(emptyList<Any>(), month.unnamedPeople)
    }
}
