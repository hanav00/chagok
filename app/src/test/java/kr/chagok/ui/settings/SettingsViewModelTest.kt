package kr.chagok.ui.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kr.chagok.R
import kr.chagok.car.NaviBookmarks
import kr.chagok.domain.ChagokConfig
import kr.chagok.domain.model.AdminEdit
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TripFixtures
import kr.chagok.testing.TripFixtures.trip
import kr.chagok.testing.ViewModelTestRule
import kr.chagok.testing.seedTrips
import kr.chagok.testing.setUpLockedAdmin
import kr.chagok.testing.setUpUnlockedAdmin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 설정 화면. 기록 방식(기본 분류·누가 쓰는 차)은 PIN 을 정한 차에서 관리자만 바꾼다.
 * 화면이 막고 ViewModel 도 한 번 더 막는다.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    @get:Rule
    val rule = ViewModelTestRule()
    private val app get() = rule.app

    private fun settings() = rule.viewModel { SettingsViewModel(app.container) }

    private suspend fun SettingsViewModel.awaitState(condition: (SettingsUiState) -> Boolean) =
        await(state) { it != null && condition(it) }!!

    @Test
    fun `혼자 쓰는 차·PIN 없음 → 관리자 칸은 PIN 정하기, 아래쪽에`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SOLO)

        val state = settings().awaitState { true }
        assertFalse(state.admin.hasPin)
        assertTrue(state.admin.canManage)
        assertFalse(state.isAdminFirst)
    }

    @Test
    fun `공용차 → PIN 이 없어도 관리자 칸이 맨 위`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김민수"))

        val state = settings().awaitState { true }
        assertFalse(state.admin.hasPin)
        assertTrue(state.isAdminFirst)
    }

    @Test
    fun `PIN 을 정하고 잠김 → 관리자 칸은 PIN 넣기, 맨 위, 기록 방식은 잠김`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SOLO)
        app.setUpLockedAdmin()

        val state = settings().awaitState { true }
        assertTrue(state.admin.hasPin)
        assertFalse(state.admin.isUnlocked)
        assertFalse(state.admin.canManage)
        assertTrue(state.isAdminFirst)
    }

    @Test
    fun `관리자가 PIN 을 풂 → 풀림 칸, 잠그기를 누르면 다시 잠김`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.setUpUnlockedAdmin()
        val settings = settings()
        assertTrue(settings.awaitState { true }.admin.isUnlocked)

        rule.onMain { settings.lockAdmin() }

        val locked = settings.awaitState { !it.admin.isUnlocked }
        assertTrue(locked.admin.hasPin)
    }

    @Test
    fun `PIN 없는 차 → 기본 분류·누가 쓰는 차를 바꿀 수 있다`() = runBlocking<Unit> {
        app.completeFirstRun()
        val settings = settings()

        rule.onMain { settings.setDefaultCategory(Category.PERSONAL) }
        rule.onMain { settings.setUsage(VehicleUsage.SHARED) }

        val state = settings.awaitState { it.settings.usage == VehicleUsage.SHARED }
        assertEquals(Category.PERSONAL, state.settings.defaultCategory)
    }

    @Test
    fun `잠긴 차 → 기본 분류·누가 쓰는 차를 눌러도 바뀌지 않는다 (음성 안내는 바뀐다)`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SOLO, defaultCategory = Category.BUSINESS)
        app.setUpLockedAdmin()
        val settings = settings()

        rule.onMain { settings.setDefaultCategory(Category.PERSONAL) }
        rule.onMain { settings.setUsage(VehicleUsage.SHARED) }
        // 설정 저장은 차례로 되므로, 막지 않는 쓰기가 보이면 앞의 두 번은 저장되지 않은 것이다
        rule.onMain { settings.setTripVoiceOn(false) }

        val state = settings.awaitState { !it.settings.isTripVoiceOn }
        assertEquals(Category.BUSINESS, state.settings.defaultCategory)
        assertEquals(VehicleUsage.SOLO, state.settings.usage)
    }

    @Test
    fun `관리자가 PIN 을 푼 차 → 기본 분류를 바꿀 수 있고 홈 다음 출발도 그 값`() = runBlocking<Unit> {
        app.completeFirstRun(defaultCategory = Category.BUSINESS)
        app.setUpUnlockedAdmin()
        val settings = settings()

        rule.onMain { settings.setDefaultCategory(Category.PERSONAL) }

        settings.awaitState { it.settings.defaultCategory == Category.PERSONAL }
        await(app.container.nextDeparture.next) { it?.category == Category.PERSONAL && !it.isChanged }
    }

    @Test
    fun `도착 음성 안내를 끔 → 다음 도착 때 말하지 않는다`() = runBlocking<Unit> {
        app.startRecording()
        app.completeFirstRun()
        val settings = settings()

        rule.onMain { settings.setTripVoiceOn(false) }
        settings.awaitState { !it.settings.isTripVoiceOn }
        app.driveTrip(km = 8.8, destination = TripFixtures.CLIENT)

        assertEquals(emptyList<String>(), app.voiceOutput.spoken)
    }

    @Test
    fun `등록한 사람은 등록 순서대로, 사업연도는 올해`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김민수", "이서연"))

        val state = settings().awaitState { it.people.size == 3 }
        assertEquals(listOf("박정훈", "김민수", "이서연"), state.people.map { it.name })
        assertEquals(2026, state.fiscalYear.year)
    }

    @Test
    fun `관리자 수정 7건 → 최근 5건만, 최근 것부터`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.setUpUnlockedAdmin()
        val trips = app.seedTrips(*Array(7) { i -> trip(startKm = i * 10.0, endKm = i * 10.0 + 5) })
        trips.forEach { app.trips.classify(it.id, Category.PERSONAL, isAdminEdit = true) }

        val state = settings().awaitState { it.recentEdits.size == ChagokConfig.ADMIN_LOG_ROWS }
        assertEquals(trips.takeLast(5).reversed().map { it.id }, state.recentEdits.map { it.tripId })
    }

    @Test
    fun `사람 수정 기록 → 기록의 사람 id 가 등록한 이름으로 보인다 (미지정이었으면 이름 없음)`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김민수"))
        app.setUpUnlockedAdmin()
        val (park, kim) = app.container.people.people.value
        val (unassigned, parks) = app.seedTrips(
            trip(personId = null),
            trip(startKm = 110.0, endKm = 120.0, personId = park.id),
        )
        app.trips.assignPerson(unassigned.id, park.id, isAdminEdit = true)
        app.trips.assignPerson(parks.id, kim.id, isAdminEdit = true)

        val state = settings().awaitState { it.recentEdits.size == 2 }
        val (latest, first) = state.recentEdits
        assertEquals(AdminEdit.Field.PERSON, latest.field)
        assertEquals("박정훈", editedPerson(latest.fromValue, state.people)?.name)
        assertEquals("김민수", editedPerson(latest.toValue, state.people)?.name)
        assertNull("미지정 → 박정훈", editedPerson(first.fromValue, state.people))
        assertEquals("박정훈", editedPerson(first.toValue, state.people)?.name)
    }

    @Test
    fun `사람을 다시 등록해 id 가 사라진 기록 → 이름 없음으로 보인다`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김민수"))

        val people = settings().awaitState { it.people.size == 2 }.people
        assertNull(editedPerson("999", people))
        assertNull(editedPerson(null, people))
    }

    @Test
    fun `분류 수정 기록 값이 지금 분류에 없는 이름이면 null (설정 화면이 죽지 않는다)`() {
        assertEquals(Category.COMMUTE, editedCategory(Category.COMMUTE.name))
        assertNull(editedCategory("OLD_NAME"))
    }

    @Test
    fun `분류 수정 기록 → 바꾸기 전·후 분류 이름이 남는다 (분류 대기에서 고른 것은 전이 비어 있다)`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.setUpUnlockedAdmin()
        val (gap) = app.seedTrips(trip(category = null, personId = null, isGap = true))
        app.trips.classify(gap.id, Category.COMMUTE, isAdminEdit = true)

        val edit = settings().awaitState { it.recentEdits.isNotEmpty() }.recentEdits.single()
        assertEquals(AdminEdit.Field.CATEGORY, edit.field)
        assertNull(edit.fromValue)
        assertEquals(Category.COMMUTE.name, edit.toValue)
    }

    @Test
    fun `사용자 위로 → 순서가 바뀌고 맨 위가 기본 사용자`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김민수"))
        val kim = app.container.people.people.value[1]
        val vm = settings()
        vm.awaitState { it.people.size == 2 }

        rule.onMain { vm.movePersonUp(kim.id) }

        assertEquals(listOf("김민수", "박정훈"), vm.awaitState { it.people[0].id == kim.id }.people.map { it.name })
    }

    @Test
    fun `잠긴 차에서는 사용자 순서를 바꾸지 않는다`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김민수"))
        app.setUpLockedAdmin()
        val kim = app.container.people.people.value[1]
        val vm = settings()
        vm.awaitState { !it.admin.canManage }

        rule.onMain { vm.movePersonUp(kim.id) }

        assertEquals(listOf("박정훈", "김민수"), app.container.people.people.value.map { it.name })
    }

    @Test
    fun `내비 즐겨찾기에 집·회사가 있으면 맞추기로 앱의 집·회사가 그 값이 된다`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.navi.bookmarks.value = NaviBookmarks(home = TripFixtures.HOME, office = TripFixtures.OFFICE)
        val vm = settings()

        rule.onMain { vm.syncPlacesFromNavi() }

        val state = vm.awaitState { it.settings.office != null }
        assertEquals(TripFixtures.HOME, state.settings.home)
        assertEquals(TripFixtures.OFFICE, state.settings.office)
        assertEquals(R.string.settings_places_synced, app.container.messages.messages.first().text)
    }

    @Test
    fun `즐겨찾기에 회사만 있으면 바꾸지 않고 둘 다 있어야 한다고 알린다`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.navi.bookmarks.value = NaviBookmarks(office = TripFixtures.OFFICE)
        val vm = settings()

        rule.onMain { vm.syncPlacesFromNavi() }

        assertEquals(R.string.settings_places_need_both, app.container.messages.messages.first().text)
        assertNull(vm.awaitState { true }.settings.office)
    }

    @Test
    fun `지금 여기를 집으로 정한 뒤 즐겨찾기에 회사만 있으면 그 둘로 맞춘다`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.navi.location = TripFixtures.STREET
        app.navi.bookmarks.value = NaviBookmarks(office = TripFixtures.OFFICE)
        val vm = settings()

        rule.onMain { vm.setPlaceHere(isHome = true) }
        vm.awaitState { it.settings.home != null }
        rule.onMain { vm.syncPlacesFromNavi() }

        val state = vm.awaitState { it.settings.office != null }
        assertEquals(TripFixtures.STREET, state.settings.home)
        assertEquals(TripFixtures.OFFICE, state.settings.office)
    }

    @Test
    fun `지금 위치를 못 읽으면 집을 바꾸지 않고 알린다`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.navi.location = null
        val vm = settings()

        rule.onMain { vm.setPlaceHere(isHome = true) }

        assertEquals(R.string.onboarding_place_here_failed_short, app.container.messages.messages.first().text)
        assertNull(vm.awaitState { true }.settings.home)
    }

    @Test
    fun `잠긴 차에서는 집·회사를 맞추지 않는다`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.setUpLockedAdmin()
        app.navi.bookmarks.value = NaviBookmarks(home = TripFixtures.HOME, office = TripFixtures.OFFICE)
        val vm = settings()
        vm.awaitState { !it.admin.canManage }

        rule.onMain { vm.syncPlacesFromNavi() }
        // 막지 않는 쓰기가 보이면 앞의 맞추기는 저장되지 않은 것이다
        rule.onMain { vm.setTripVoiceOn(false) }

        assertNull(vm.awaitState { !it.settings.isTripVoiceOn }.settings.office)
    }
}
