package kr.chagok.ui.shell

import app.cash.turbine.test
import kotlinx.coroutines.runBlocking
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TripFixtures.trip
import kr.chagok.testing.ViewModelTestRule
import kr.chagok.testing.seedTrips
import kr.chagok.testing.setUpLockedAdmin
import kr.chagok.testing.setUpUnlockedAdmin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 앱 껍데기: 첫 실행 화면이냐 탭 화면이냐, 주행 중 화면, 헤더 "관리자", 홈 탭 배지.
 */
@RunWith(RobolectricTestRunner::class)
class ShellViewModelTest {
    @get:Rule
    val rule = ViewModelTestRule()
    private val app get() = rule.app

    private fun shell() = rule.viewModel { ShellViewModel(app.container) }

    @Test
    fun `앱을 막 설치함 → 첫 실행 화면, 주행 중 아님, 배지 없음`() = runBlocking<Unit> {
        val state = await(shell().state) { it != null }!!

        assertFalse(state.isFirstRunDone)
        assertFalse(state.isDriving)
        assertFalse(state.isAdminUnlocked)
        assertEquals(0, state.badgeCount)
    }

    @Test
    fun `첫 실행을 마침 → 탭 화면으로`() = runBlocking<Unit> {
        val shell = shell()
        app.completeFirstRun()

        await(shell.state) { it?.isFirstRunDone == true }
    }

    @Test
    fun `기본값 업무로 출발 → 주행 중 화면에 업무`() = runBlocking<Unit> {
        app.startRecording()
        app.completeFirstRun(defaultCategory = Category.BUSINESS)
        val shell = shell()

        app.startDriving()

        val state = await(shell.state) { it?.isDriving == true }!!
        assertEquals(Category.BUSINESS, state.drivingCategory)
    }

    @Test
    fun `다음 출발을 개인으로 바꾸고 출발 → 주행 중 화면에 개인, 주차하면 주행 중 화면이 닫힌다`() = runBlocking<Unit> {
        app.startRecording()
        app.completeFirstRun(defaultCategory = Category.BUSINESS)
        val shell = shell()
        app.container.nextDeparture.pickCategory(Category.PERSONAL)
        await(app.container.nextDeparture.next) { it?.category == Category.PERSONAL }

        app.startDriving()
        val driving = await(shell.state) { it?.isDriving == true }!!
        assertEquals(Category.PERSONAL, driving.drivingCategory)

        app.vehicle.drive(3.0)
        app.park()
        // 운행이 끝나면 다음 출발은 다시 기본값
        await(shell.state) { it?.isDriving == false && it.drivingCategory == Category.BUSINESS }
    }

    @Test
    fun `공용차 배지 = 분류 대기 + 사용자 미지정`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김민수"))
        app.seedTrips(
            trip(startKm = 100.0, endKm = 105.0, category = null, personId = null, isGap = true), // 분류 대기
            trip(startKm = 105.0, endKm = 110.0, personId = null), // 미지정
            trip(startKm = 110.0, endKm = 115.0, personId = null), // 미지정
            trip(startKm = 115.0, endKm = 120.0, personId = 1), // 다 정해짐
        )

        await(shell().state) { it?.badgeCount == 3 }
    }

    @Test
    fun `잠긴 공용차에서 자정이 지남 → 어제가 된 미지정은 운전자가 못 고르니 배지에서 빠진다`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김민수"))
        app.setUpLockedAdmin()
        app.seedTrips(trip(personId = null))
        val shell = shell()
        await(shell.state) { it?.badgeCount == 1 }

        app.passDays(1)

        await(shell.state) { it?.badgeCount == 0 }
    }

    @Test
    fun `혼자 쓰는 차 배지 = 분류 대기만 (사람을 묻지 않으니 미지정은 세지 않는다)`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SOLO)
        app.seedTrips(
            trip(startKm = 100.0, endKm = 105.0, category = null, personId = null, isGap = true),
            trip(startKm = 105.0, endKm = 110.0, personId = null),
        )

        val state = await(shell().state) { it?.isFirstRunDone == true && it.badgeCount > 0 }!!
        assertEquals(1, state.badgeCount)
    }

    @Test
    fun `분류 대기를 고르면 배지가 줄어든다`() = runBlocking<Unit> {
        app.completeFirstRun()
        val gap = app.seedTrips(
            trip(startKm = 100.0, endKm = 105.0, category = null, personId = null, isGap = true),
        ).single()
        val shell = shell()
        await(shell.state) { it?.badgeCount == 1 }

        app.trips.classify(gap.id, Category.BUSINESS)

        await(shell.state) { it?.badgeCount == 0 }
    }

    @Test
    fun `관리자 PIN 을 풀면 헤더에 관리자, 잠그기를 누르면 사라진다`() = runBlocking<Unit> {
        app.completeFirstRun()
        val shell = shell()
        app.setUpUnlockedAdmin()
        await(shell.state) { it?.isAdminUnlocked == true }

        rule.onMain { shell.lockAdmin() }

        await(shell.state) { it?.isAdminUnlocked == false }
    }

    @Test
    fun `관리자 모드에서 출발 → 저절로 잠긴다`() = runBlocking<Unit> {
        app.startRecording()
        app.completeFirstRun()
        val shell = shell()
        app.setUpUnlockedAdmin()
        await(shell.state) { it?.isAdminUnlocked == true }

        app.startDriving()

        // 운전자 권한으로 시작한다 (잠기지 않으면 기다리다 시간 초과로 실패)
        await(shell.state) { it?.isDriving == true && !it.isAdminUnlocked }
    }

    @Test
    fun `주차해서 운행이 저장되면 홈으로 가라는 신호가 온다 (앱을 켤 때 남아 있던 값은 무시)`() = runBlocking<Unit> {
        app.startRecording()
        app.completeFirstRun()
        val shell = shell()

        shell.recentTripShown.test {
            val trip = app.driveTrip(km = 4.0)
            assertEquals(trip.id, awaitItem())
            expectNoEvents()
        }
    }
}
