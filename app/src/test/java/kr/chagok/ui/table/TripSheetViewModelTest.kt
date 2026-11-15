package kr.chagok.ui.table

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kr.chagok.domain.model.AdminEdit
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ClassifyReason
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.testing.ScriptedVoiceInput
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TripFixtures
import kr.chagok.testing.TripFixtures.TODAY
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
 * 운행 상세 시트: 기록부 줄을 누르면 열려 분류를 고친다.
 * 사람 고르기는 공용차에서 미지정이면 누구나, 이미 정해진 사람은 관리 권한이 있을 때만 하고 기록 안 된 주행은 묻지 않는다.
 * 관리자 모드에서 고치면 근거가 "관리자 수정"이 되고 수정 기록이 남는다.
 */
@RunWith(RobolectricTestRunner::class)
class TripSheetViewModelTest {
    @get:Rule
    val rule = ViewModelTestRule()
    private val app get() = rule.app

    private suspend fun open(tripId: Long): TripSheetViewModel {
        val sheet = rule.viewModel { TripSheetViewModel(app.container) }
        rule.onMain { sheet.show(tripId) }
        await(sheet.state) { it?.tripId == tripId && it.trip != null }
        return sheet
    }

    private suspend fun TripSheetViewModel.awaitState(condition: (TripSheetUiState) -> Boolean) =
        await(state) { it != null && condition(it) }!!

    private suspend fun sharedCar() = app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김민수"))

    @Test
    fun `운행을 엶 → 그 운행과 탄 사람`() = runBlocking<Unit> {
        sharedCar()
        val kim = app.container.people.people.value[1]
        val (trip) = app.seedTrips(trip(personId = kim.id))

        val state = open(trip.id).state.value!!

        assertEquals(trip, state.trip)
        assertEquals("김민수", state.person?.name)
        assertEquals(2, state.people.size)
    }

    @Test
    fun `다른 운행을 열면 → 그 운행으로 바뀐다`() = runBlocking<Unit> {
        app.completeFirstRun()
        val (first, second) = app.seedTrips(trip(startKm = 0.0, endKm = 10.0), trip(startKm = 10.0, endKm = 25.0))
        val sheet = open(first.id)

        rule.onMain { sheet.show(second.id) }

        assertEquals(15.0, sheet.awaitState { it.tripId == second.id }.trip!!.km, 1e-9)
    }

    @Test
    fun `혼자 쓰는 차 → 사람을 고르지 않는다`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SOLO)
        val (trip) = app.seedTrips(trip(personId = null))

        assertFalse(open(trip.id).state.value!!.isPersonPickable)
    }

    @Test
    fun `사람이 없는 운행의 사용자 칸 → 공용차만 미지정, 혼자 쓰는 차·기록 안 된 주행은 아니다 (기록부 ④칸과 같다)`() = runBlocking<Unit> {
        sharedCar()
        val (unassigned, gap) =
            app.seedTrips(trip(personId = null), trip(category = null, personId = null, isGap = true))
        assertTrue(open(unassigned.id).state.value!!.isUnassigned)
        assertFalse(open(gap.id).state.value!!.isUnassigned)

        app.settings.setUsage(VehicleUsage.SOLO)
        val solo = open(unassigned.id)
        await(solo.state) { it?.isUnassigned == false }
    }

    @Test
    fun `공용차·잠김 → 미지정 운행은 운전자도 고를 수 있다`() = runBlocking<Unit> {
        sharedCar()
        app.setUpLockedAdmin()
        val (trip) = app.seedTrips(trip(personId = null, date = TODAY))

        assertTrue(open(trip.id).state.value!!.isPersonPickable)
    }

    @Test
    fun `공용차·잠김 → 이미 정해진 사람은 운전자가 바꿀 수 없다`() = runBlocking<Unit> {
        sharedCar()
        app.setUpLockedAdmin()
        val (trip) = app.seedTrips(trip(personId = 1))

        assertFalse(open(trip.id).state.value!!.isPersonPickable)
    }

    @Test
    fun `공용차·관리자 → 이미 정해진 사람도 바꿀 수 있다`() = runBlocking<Unit> {
        sharedCar()
        app.setUpUnlockedAdmin()
        val (trip) = app.seedTrips(trip(personId = 1))

        assertTrue(open(trip.id).state.value!!.isPersonPickable)
    }

    @Test
    fun `공용차·PIN 없음 → 정해진 사람도 누구나 바꿀 수 있다`() = runBlocking<Unit> {
        sharedCar()
        val (trip) = app.seedTrips(trip(personId = 1))

        assertTrue(open(trip.id).state.value!!.isPersonPickable)
    }

    @Test
    fun `기록 안 된 주행 → 사람을 묻지 않는다`() = runBlocking<Unit> {
        sharedCar()
        val (gap) = app.seedTrips(trip(category = null, personId = null, isGap = true))

        assertFalse(open(gap.id).state.value!!.isPersonPickable)
    }

    @Test
    fun `운전자가 미지정 운행에 사람을 고름 → 그 사람으로 남고 수정 기록은 없다`() = runBlocking<Unit> {
        sharedCar()
        app.setUpLockedAdmin()
        val (trip) = app.seedTrips(trip(personId = null))
        val kim = app.container.people.people.value[1]
        val sheet = open(trip.id)

        rule.onMain { sheet.assignPerson(kim.id) }

        assertEquals("김민수", sheet.awaitState { it.person != null }.person!!.name)
        assertTrue(app.trips.adminEdits.first().isEmpty())
    }

    @Test
    fun `관리자가 사람을 바꿈 → 수정 기록에 사람 바꾸기 전·후가 남는다`() = runBlocking<Unit> {
        sharedCar()
        app.setUpUnlockedAdmin()
        val (park, kim) = app.container.people.people.value
        val (trip) = app.seedTrips(trip(personId = park.id))
        val sheet = open(trip.id)

        rule.onMain { sheet.assignPerson(kim.id) }

        val edit = await(app.trips.adminEdits) { it.isNotEmpty() }.single()
        assertEquals(AdminEdit.Field.PERSON, edit.field)
        assertEquals(park.id.toString(), edit.fromValue)
        assertEquals(kim.id.toString(), edit.toValue)
        assertEquals(trip.id, edit.tripId)
    }

    @Test
    fun `PIN 없는 차에서 개인으로 → 근거 사용자 수정, 닫기 전까지 장소를 되살릴 수 있다`() = runBlocking<Unit> {
        app.completeFirstRun()
        val (trip) = app.seedTrips(trip(destination = TripFixtures.CLIENT))
        val sheet = open(trip.id)

        rule.onMain { sheet.classify(Category.PERSONAL) }

        val state = sheet.awaitState { it.trip?.isPersonal == true }
        assertEquals(ClassifyReason.USER_EDIT, state.trip!!.reason)
        assertNull(state.trip!!.destination)
        assertTrue("닫으면 장소를 지워요", state.isPlaceRestorable)
        assertTrue(app.trips.adminEdits.first().isEmpty())
    }

    @Test
    fun `개인으로 바꾸고 다시 업무로 → 장소가 돌아온다`() = runBlocking<Unit> {
        app.completeFirstRun()
        val (trip) = app.seedTrips(trip(destination = TripFixtures.CLIENT))
        val sheet = open(trip.id)
        rule.onMain { sheet.classify(Category.PERSONAL) }
        sheet.awaitState { it.isPlaceRestorable && it.trip?.isPersonal == true }

        rule.onMain { sheet.classify(Category.BUSINESS) }

        val state = sheet.awaitState { it.trip?.category == Category.BUSINESS }
        assertEquals(TripFixtures.CLIENT, state.trip!!.destination)
        assertEquals(ClassifyReason.DEFAULT_BUSINESS, state.trip!!.reason)
        assertFalse(state.isPlaceRestorable)
    }

    @Test
    fun `개인으로 바꾸고 시트를 닫음 → 장소 삭제 확정, 다시 업무로 바꿔도 장소는 없다`() = runBlocking<Unit> {
        app.completeFirstRun()
        val (trip) = app.seedTrips(trip(destination = TripFixtures.CLIENT))
        val sheet = open(trip.id)
        rule.onMain { sheet.classify(Category.PERSONAL) }
        sheet.awaitState { it.isPlaceRestorable && it.trip?.isPersonal == true }

        rule.onMain { sheet.commitPlaceDeletion(trip.id) }
        sheet.awaitState { !it.isPlaceRestorable }
        rule.onMain { sheet.classify(Category.BUSINESS) }

        val state = sheet.awaitState { it.trip?.category == Category.BUSINESS }
        assertNull(state.trip!!.destination)
        assertEquals(ClassifyReason.USER_EDIT, state.trip!!.reason)
    }

    @Test
    fun `관리자 모드에서 지난 운행을 개인으로 → 근거 관리자 수정, 수정 기록 업무→개인`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.setUpUnlockedAdmin()
        val (trip) = app.seedTrips(trip(date = TODAY.minusDays(5)))
        val sheet = open(trip.id)

        rule.onMain { sheet.classify(Category.PERSONAL) }

        val state = sheet.awaitState { it.trip?.isPersonal == true }
        assertEquals(ClassifyReason.ADMIN_EDIT, state.trip!!.reason)
        val edit = await(app.trips.adminEdits) { it.isNotEmpty() }.single()
        assertEquals(AdminEdit.Field.CATEGORY, edit.field)
        assertEquals(Category.BUSINESS.name, edit.fromValue)
        assertEquals(Category.PERSONAL.name, edit.toValue)
        assertEquals(TODAY.minusDays(5), edit.tripDate)
    }

    @Test
    fun `시트를 연 채 관리자가 잠금 → 그 뒤 고친 것은 사용자 수정`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.setUpUnlockedAdmin()
        val (trip) = app.seedTrips(trip())
        val sheet = open(trip.id)

        app.container.admin.lock()
        await(app.container.admin.state) { !it.isAdminEdit }
        rule.onMain { sheet.classify(Category.PERSONAL) }

        val state = sheet.awaitState { it.trip?.isPersonal == true }
        assertEquals(ClassifyReason.USER_EDIT, state.trip!!.reason)
        assertTrue(app.trips.adminEdits.first().isEmpty())
    }

    @Test
    fun `기록 안 된 주행을 고름 → 근거 사용자 선택`() = runBlocking<Unit> {
        app.completeFirstRun()
        val (gap) = app.seedTrips(trip(category = null, personId = null, isGap = true))
        val sheet = open(gap.id)

        rule.onMain { sheet.classify(Category.COMMUTE) }

        val state = sheet.awaitState { it.trip?.category == Category.COMMUTE }
        assertEquals(ClassifyReason.USER_PICK, state.trip!!.reason)
        assertFalse("기록 안 된 주행은 원래 장소가 없어 되살릴 것도 없다", state.isPlaceRestorable)
    }

    @Test
    fun `방문 목적을 말함 → 그 운행 비고에 남고 듣기가 끝난다`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.voiceInput = ScriptedVoiceInput("단가 협상")
        val (trip) = app.seedTrips(trip())
        val sheet = open(trip.id)

        rule.onMain { sheet.dictate() }

        assertEquals("단가 협상", sheet.awaitState { it.trip?.note?.isNotEmpty() == true }.trip!!.note)
        assertFalse(await(sheet.isListening) { !it })
    }

    @Test
    fun `못 알아들음 → 앞에 남긴 말 그대로, 다시 말하면 새 말로 바뀐다`() = runBlocking<Unit> {
        app.completeFirstRun()
        app.voiceInput = ScriptedVoiceInput(null, "납품 검수")
        val (trip) = app.seedTrips(trip(note = "견적 협의"))
        val sheet = open(trip.id)

        rule.onMain { sheet.dictate() }
        await(sheet.isListening) { !it }
        assertEquals("견적 협의", app.trips.find(trip.id)!!.note)

        rule.onMain { sheet.dictate() }
        assertEquals("납품 검수", sheet.awaitState { it.trip?.note == "납품 검수" }.trip!!.note)
    }
}
