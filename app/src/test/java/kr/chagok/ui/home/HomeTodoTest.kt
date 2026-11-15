package kr.chagok.ui.home

import kotlinx.coroutines.runBlocking
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.domain.model.EnergyKind
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.testing.ScriptedVoiceInput
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TestApp.Companion.awaitValue
import kr.chagok.testing.TripFixtures.TODAY
import kr.chagok.testing.TripFixtures.trip
import kr.chagok.testing.ViewModelTestRule
import kr.chagok.testing.seedTrips
import kr.chagok.testing.setUpLockedAdmin
import kr.chagok.testing.setUpUnlockedAdmin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 홈의 할 일: "기록 안 된 주행" 분류 대기 목록, "누가 탔는지 골라 주세요", "주유·충전 금액".
 * 미지정은 공용차만 묻고, PIN 으로 잠긴 차의 운전자는 오늘 것만 고르고 지난 것은 잠긴 줄(수)로만 본다.
 */
@RunWith(RobolectricTestRunner::class)
class HomeTodoTest {
    @get:Rule
    val rule = ViewModelTestRule()
    private val app get() = rule.app

    private fun home() = rule.viewModel { HomeViewModel(app.container) }

    @Test
    fun `앱이 꺼진 사이 8·2km 주행 → 요약 카드에 경고, 다음 출발 때 분류 대기에 8·2km 한 줄`() = runBlocking<Unit> {
        app.startRecording()
        app.completeFirstRun()
        val home = home()
        app.driveTrip(km = 3.0) // 앞 운행이 있어야 빠진 거리를 잴 기준이 있다

        app.driveWhileAppOff(8.2)
        val warned = await(home.state) { (it?.summary?.unrecordedKm ?: 0.0) > 0 }!!
        assertEquals(8.2, warned.summary.unrecordedKm, 1e-9)
        assertEquals("출발 전에는 아직 분류 대기가 아니다", emptyList<Any>(), warned.pendingTrips)

        app.driveTrip(km = 3.0)

        val state = await(home.state) { it?.pendingTrips?.isNotEmpty() == true }!!
        val gap = state.pendingTrips.single()
        assertEquals(8.2, gap.km, 1e-9)
        assertEquals(true, gap.isGap)
        assertEquals(1_003.0, gap.startKm, 1e-9)
    }

    @Test
    fun `기록 안 된 주행 장면 → 맨 앞 하나와 그날 앞뒤 두 운행씩으로 쌓은 탑, 다른 날 운행은 빠진다`() = runBlocking<Unit> {
        app.completeFirstRun()
        val trips = app.seedTrips(
            trip(startKm = 0.0, endKm = 10.0, date = TODAY.minusDays(1)),
            trip(startKm = 10.0, endKm = 20.0, category = Category.COMMUTE),
            trip(startKm = 20.0, endKm = 30.0),
            trip(startKm = 30.0, endKm = 31.0),
            trip(startKm = 31.0, endKm = 39.0, category = null, personId = null, isGap = true),
            trip(startKm = 39.0, endKm = 50.0),
            trip(startKm = 50.0, endKm = 60.0, category = null, personId = null, isGap = true),
        )
        val gap = trips[4]

        val scene = await(home().state) { it?.gap != null }!!.gap!!
        assertEquals(gap.id, scene.trip.id)
        assertEquals(trips.subList(2, 7).map { it.id }, scene.dayStack.map { it.id })
    }

    @Test
    fun `분류 대기를 업무로 고름 → 목록에서 빠지고 근거 사용자 선택`() = runBlocking<Unit> {
        app.completeFirstRun()
        val gap = app.seedTrips(
            trip(startKm = 100.0, endKm = 108.2, category = null, personId = null, isGap = true),
        ).single()
        val home = home()
        await(home.state) { it?.pendingTrips?.size == 1 }

        rule.onMain { home.classify(gap.id, Category.BUSINESS) }

        await(home.state) { it?.pendingTrips?.isEmpty() == true }
        val saved = app.trips.trips.value.single()
        assertEquals(Category.BUSINESS, saved.category)
        assertEquals(ClassifyReason.USER_PICK, saved.reason)
    }

    @Test
    fun `관리자 모드에서 분류 대기를 고름 → 근거 관리자 수정, 수정 기록이 남는다 (운행 상세 시트와 같다)`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.setUpUnlockedAdmin()
        val gap = app.seedTrips(
            trip(startKm = 100.0, endKm = 108.2, category = null, personId = null, isGap = true),
        ).single()
        val home = home()
        await(home.state) { it?.pendingTrips?.size == 1 }

        rule.onMain { home.classify(gap.id, Category.BUSINESS) }

        await(home.state) { it?.pendingTrips?.isEmpty() == true }
        assertEquals(ClassifyReason.ADMIN_EDIT, app.trips.trips.value.single().reason)
        assertEquals(Category.BUSINESS.name, await(app.trips.adminEdits) { it.isNotEmpty() }.single().toValue)
    }

    @Test
    fun `혼자 쓰는 차 → 사람이 비어 있는 운행이 있어도 미지정 칸이 없다`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SOLO)
        app.seedTrips(trip(personId = null))

        val state = await(home().state) { it?.summary?.monthTotals?.total == 10.0 }!! // 넣은 운행이 반영된 상태
        assertNull(state.unassigned)
    }

    @Test
    fun `공용차·PIN 없음 → 오늘·지난 미지정 모두 바로 고를 수 있다`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김민수"))
        val (yesterday, today) = app.seedTrips(
            trip(startKm = 100.0, endKm = 110.0, date = TODAY.minusDays(1), personId = null),
            trip(startKm = 110.0, endKm = 120.0, date = TODAY, personId = null),
        )

        val unassigned = await(home().state) { it?.unassigned != null }!!.unassigned!!
        assertEquals(listOf(yesterday.id, today.id), unassigned.openTrips.map { it.id })
        assertEquals(0, unassigned.lockedCount)
        assertEquals(2, unassigned.count)
    }

    @Test
    fun `공용차·PIN 잠김 → 오늘 것만 고르고 지난 것은 잠긴 줄에 수만`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김민수"))
        app.setUpLockedAdmin()
        val (_, _, today) = app.seedTrips(
            trip(startKm = 90.0, endKm = 100.0, date = TODAY.minusDays(3), personId = null),
            trip(startKm = 100.0, endKm = 110.0, date = TODAY.minusDays(1), personId = null),
            trip(startKm = 110.0, endKm = 120.0, date = TODAY, personId = null),
        )

        val unassigned = await(home().state) { it?.unassigned != null }!!.unassigned!!
        assertEquals(listOf(today.id), unassigned.openTrips.map { it.id })
        assertEquals(2, unassigned.lockedCount)
        assertEquals(3, unassigned.count)
    }

    @Test
    fun `공용차·관리자가 PIN 을 풂 → 지난 미지정도 바로 고를 수 있다`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김민수"))
        app.setUpUnlockedAdmin()
        app.seedTrips(
            trip(startKm = 100.0, endKm = 110.0, date = TODAY.minusDays(1), personId = null),
            trip(startKm = 110.0, endKm = 120.0, date = TODAY, personId = null),
        )

        val unassigned = await(home().state) { it?.unassigned != null }!!.unassigned!!
        assertEquals(2, unassigned.openTrips.size)
        assertEquals(0, unassigned.lockedCount)
    }

    @Test
    fun `미지정 운행에 사람을 고름 → 목록에서 빠지고, 다 고르면 칸이 사라진다`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김민수"))
        val (first, second) = app.seedTrips(
            trip(startKm = 100.0, endKm = 110.0, personId = null),
            trip(startKm = 110.0, endKm = 120.0, personId = null),
        )
        val home = home()
        await(home.state) { it?.unassigned?.count == 2 }
        val kim = app.container.people.people.value[1]

        rule.onMain { home.assignPerson(first.id, kim.id) }
        val left = await(home.state) { it?.unassigned?.count == 1 }!!.unassigned!!
        assertEquals(listOf(second.id), left.openTrips.map { it.id })

        rule.onMain { home.assignPerson(second.id, kim.id) }
        await(home.state) { it != null && it.unassigned == null }
        assertEquals(kim.id, app.trips.trips.value.first { it.id == first.id }.personId)
    }

    @Test
    fun `기록 안 된 주행은 시각도 몰라 사람을 묻지 않는다 (분류 대기에만 있다)`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김민수"))
        app.seedTrips(trip(category = null, personId = null, isGap = true))

        val state = await(home().state) { it?.pendingTrips?.size == 1 }!!
        assertNull(state.unassigned)
    }

    @Test
    fun `충전을 알아채면 주유·충전 금액에 한 줄, 말로 금액을 넣으면 빠진다`() = runBlocking<Unit> {
        app.startRecording()
        app.completeFirstRun()
        val home = home()
        app.vehicle.batteryPercent.value = 23
        awaitValue({ app.container.refuels.mark(EnergyKind.CHARGE) }) { it?.percent == 23 }

        app.vehicle.batteryPercent.value = 81

        val todo = await(home.state) { it?.refuel != null }!!.refuel!!
        val charge = todo.refuels.single()
        assertEquals(EnergyKind.CHARGE, charge.kind)
        assertEquals(23 to 81, charge.fromPercent to charge.toPercent)

        app.voiceInput = ScriptedVoiceInput("만 8천 원")
        rule.onMain { home.dictateRefuelAmount(charge.id) }

        await(home.state) { it?.refuel == null }
        assertEquals(18_000L, app.container.refuels.refuels.value.single().amountWon)
    }

    @Test
    fun `모름으로 넘긴 주유는 할 일에서 빠지고 금액은 비어 있다`() = runBlocking<Unit> {
        app.startRecording()
        app.completeFirstRun()
        val home = home()
        app.vehicle.fuelPercent.value = 30
        awaitValue({ app.container.refuels.mark(EnergyKind.FUEL) }) { it?.percent == 30 }
        app.vehicle.fuelPercent.value = 90
        val refuel = await(home.state) { it?.refuel != null }!!.refuel!!.refuels.single()

        rule.onMain { home.skipRefuelAmount(refuel.id) }

        await(home.state) { it?.refuel == null }
        assertNull(app.container.refuels.refuels.value.single().amountWon)
    }
}
