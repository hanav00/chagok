package kr.chagok.ui.people

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kr.chagok.domain.ChagokConfig
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TripFixtures.trip
import kr.chagok.testing.ViewModelTestRule
import kr.chagok.testing.seedTrips
import kr.chagok.testing.setUpLockedAdmin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 사람 시트. 첫 실행에서 이름 없이 넣은 사람도 여기서 이름을 넣는다.
 * 이름·부서는 [ChagokConfig.PERSON_TEXT_MAX] 자까지, 지난 운행에 나오는 사람은 지우지 않고 숨기며
 * 쓰는 사람이 한 명뿐이면 못 지운다.
 */
@RunWith(RobolectricTestRunner::class)
class PersonSheetViewModelTest {
    @get:Rule
    val rule = ViewModelTestRule()
    private val app get() = rule.app

    private fun sheet() = rule.viewModel { PersonSheetViewModel(app.container) }

    private suspend fun PersonSheetViewModel.awaitState(condition: (PersonSheetUiState) -> Boolean) =
        await(state) { it != null && condition(it) }!!

    /** [action] 에 넘긴 "끝나면" 콜백이 불릴 때까지 기다린다 (저장 후 시트 닫기) */
    private suspend fun PersonSheetViewModel.pressAndAwaitDone(action: PersonSheetViewModel.(() -> Unit) -> Unit) {
        val done = CompletableDeferred<Unit>()
        rule.onMain { action { done.complete(Unit) } }
        withTimeout(AWAIT_MILLIS) { done.await() }
    }

    private suspend fun twoPeople() = app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("박정훈", "김민수"))

    @Test
    fun `사람 추가 → 빈 칸으로 열리고, 이름·부서를 넣고 저장하면 맨 뒤에 붙는다`() = runBlocking<Unit> {
        twoPeople()
        val vm = sheet()
        rule.onMain { vm.open(PersonSheetTarget.NEW) }
        val opened = vm.awaitState { true }
        assertTrue(opened.form.isNew)
        assertEquals("", opened.form.name)

        rule.onMain {
            vm.editName("이서연")
            vm.editDept("총무팀")
        }
        vm.pressAndAwaitDone { save(onSaved = it) }

        val people = await(app.people.people) { it.size == 3 }
        assertEquals("이서연", people.last().name)
        assertEquals("총무팀", people.last().dept)
    }

    @Test
    fun `저장을 빠르게 두 번 눌러도 한 사람만 더해진다`() = runBlocking<Unit> {
        twoPeople()
        val vm = sheet()
        rule.onMain { vm.open(PersonSheetTarget.NEW) }
        vm.awaitState { it.form.isNew }
        rule.onMain { vm.editName("이서연") }

        vm.pressAndAwaitDone { onSaved ->
            save(onSaved)
            save(onSaved = {})
        }
        delay(SETTLE_MILLIS) // 두 번째 저장이 돌았다면 끝날 시간

        assertEquals(listOf("박정훈", "김민수", "이서연"), app.people.allPeople.value.map { it.name })
    }

    @Test
    fun `저장 중에 시트를 닫고 새로 열면 늦게 끝난 저장이 새 시트를 닫지 않는다`() = runBlocking<Unit> {
        twoPeople()
        val vm = sheet()
        rule.onMain { vm.open(PersonSheetTarget.NEW) }
        vm.awaitState { it.form.isNew }
        rule.onMain { vm.editName("이서연") }

        var isClosed = false
        rule.onMain {
            vm.save(onSaved = { isClosed = true })
            vm.close()
            vm.open(PersonSheetTarget.NEW)
        }
        await(app.people.people) { it.size == 3 }
        delay(SETTLE_MILLIS)

        assertFalse(rule.onMain { isClosed })
    }

    @Test
    fun `이름 없는 사람을 열면 빈 이름이 보이고, 이름을 넣고 저장하면 서식에 나갈 이름이 된다`() = runBlocking<Unit> {
        twoPeople()
        val kim = app.people.people.value[1]
        app.people.update(kim.id, "", "")
        await(app.people.people) { !it[1].isNamed }
        val vm = sheet()
        rule.onMain { vm.open(kim.id) }
        assertEquals("", vm.awaitState { it.form.person?.id == kim.id }.form.name)

        rule.onMain { vm.editName("김민수") }
        vm.pressAndAwaitDone { save(onSaved = it) }

        assertEquals("김민수", await(app.people.people) { it[1].isNamed }[1].name)
    }

    @Test
    fun `이름·부서는 정해 둔 글자 수까지만 받는다`() = runBlocking<Unit> {
        twoPeople()
        val vm = sheet()
        rule.onMain { vm.open(PersonSheetTarget.NEW) }
        vm.awaitState { true }

        rule.onMain {
            vm.editName("가".repeat(ChagokConfig.PERSON_TEXT_MAX + 5))
            vm.editDept("나".repeat(ChagokConfig.PERSON_TEXT_MAX + 5))
        }

        val form = vm.awaitState { it.form.name.isNotEmpty() }.form
        assertEquals(ChagokConfig.PERSON_TEXT_MAX, form.name.length)
        assertEquals(ChagokConfig.PERSON_TEXT_MAX, form.dept.length)
    }

    @Test
    fun `꽉 찼으면(최대 인원) 새 사람은 저장하지 않는다`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SHARED, people = listOf("가", "나", "다", "라"))
        val vm = sheet()
        rule.onMain { vm.open(PersonSheetTarget.NEW) }
        assertTrue(vm.awaitState { true }.isFull)

        rule.onMain {
            vm.editName("마")
            vm.save(onSaved = {})
        }

        assertEquals(ChagokConfig.PEOPLE_MAX, app.people.allPeople.value.size)
    }

    @Test
    fun `운행에 안 나오는 사람 → 지우기를 한 번 더 묻고(지운다고), 누르면 정말 지워진다`() = runBlocking<Unit> {
        twoPeople()
        val kim = app.people.people.value[1]
        val vm = sheet()
        rule.onMain { vm.open(kim.id) }
        assertFalse(vm.awaitState { it.form.person?.id == kim.id }.form.isUsed)

        rule.onMain { vm.askRemove() }
        vm.awaitState { it.form.isConfirmingRemove }
        vm.pressAndAwaitDone { remove(onDone = it) }

        await(app.people.allPeople) { list -> list.none { it.id == kim.id } }
    }

    @Test
    fun `지난 운행에 나오는 사람 → 숨긴다고 묻고, 누르면 숨겨져 누가 타나요에서만 빠진다`() = runBlocking<Unit> {
        twoPeople()
        val kim = app.people.people.value[1]
        app.seedTrips(trip(personId = kim.id))
        val vm = sheet()
        rule.onMain { vm.open(kim.id) }
        assertTrue(vm.awaitState { it.form.person?.id == kim.id }.form.isUsed)

        rule.onMain { vm.askRemove() }
        vm.awaitState { it.form.isConfirmingRemove }
        vm.pressAndAwaitDone { remove(onDone = it) }

        assertEquals(listOf("박정훈"), await(app.people.people) { it.size == 1 }.map { it.name })
        assertTrue(app.people.find(kim.id)!!.isHidden)
    }

    @Test
    fun `되묻는 중에 그대로 두기를 누르면 아무것도 지우지 않는다`() = runBlocking<Unit> {
        twoPeople()
        val kim = app.people.people.value[1]
        val vm = sheet()
        rule.onMain { vm.open(kim.id) }
        vm.awaitState { it.form.person != null }
        rule.onMain { vm.askRemove() }
        vm.awaitState { it.form.isConfirmingRemove }

        rule.onMain { vm.cancelRemove() }

        vm.awaitState { !it.form.isConfirmingRemove }
        assertEquals(2, app.people.people.value.size)
    }

    @Test
    fun `쓰는 사람이 한 명뿐이면 지울 수 없다 (혼자 쓰는 차의 기본 사용자)`() = runBlocking<Unit> {
        app.completeFirstRun(usage = VehicleUsage.SOLO, people = listOf("박정훈"))
        val park = app.people.people.value.single()
        val vm = sheet()
        rule.onMain { vm.open(park.id) }

        assertFalse(vm.awaitState { it.form.person != null }.canRemove)
        rule.onMain { vm.askRemove() }
        assertFalse(vm.awaitState { true }.form.isConfirmingRemove)
    }

    @Test
    fun `숨긴 사람 → 다시 쓰기를 누르면 누가 타나요에 돌아온다`() = runBlocking<Unit> {
        twoPeople()
        val kim = app.people.people.value[1]
        app.seedTrips(trip(personId = kim.id))
        app.people.remove(kim.id)
        await(app.people.people) { it.size == 1 }
        val vm = sheet()
        rule.onMain { vm.open(kim.id) }
        assertTrue(vm.awaitState { it.form.person != null }.form.person!!.isHidden)

        vm.pressAndAwaitDone { unhide(onDone = it) }

        await(app.people.people) { it.size == 2 }
    }

    @Test
    fun `쓰는 사람이 꽉 찼으면 숨긴 사람을 다시 쓸 수 없다 (상한을 넘지 않는다)`() = runBlocking<Unit> {
        twoPeople()
        val kim = app.people.people.value[1]
        app.seedTrips(trip(personId = kim.id))
        app.people.remove(kim.id)
        repeat(ChagokConfig.PEOPLE_MAX - 1) { app.people.add("사람 $it", "") }
        await(app.people.people) { it.size == ChagokConfig.PEOPLE_MAX }
        val vm = sheet()
        rule.onMain { vm.open(kim.id) }
        assertTrue(vm.awaitState { it.form.person != null }.isFull)

        rule.onMain { vm.unhide(onDone = {}) }
        delay(SETTLE_MILLIS)

        assertTrue(app.people.find(kim.id)!!.isHidden)
        assertEquals(ChagokConfig.PEOPLE_MAX, app.people.people.value.size)
    }

    @Test
    fun `PIN 을 정한 차가 잠겨 있으면 고칠 수 없다 (시트가 닫히고 저장도 막는다)`() = runBlocking<Unit> {
        twoPeople()
        app.setUpLockedAdmin()
        val kim = app.people.people.value[1]
        val vm = sheet()
        rule.onMain { vm.open(kim.id) }
        assertFalse(vm.awaitState { it.form.person != null }.canManage)

        rule.onMain {
            vm.editName("최민수")
            vm.save(onSaved = {})
        }

        assertEquals("김민수", app.people.find(kim.id)!!.name)
    }

    @Test
    fun `열려던 사람이 그사이 지워졌으면 시트를 닫는다`() = runBlocking<Unit> {
        twoPeople()
        val vm = sheet()

        rule.onMain { vm.open(999) }

        assertTrue(vm.awaitState { true }.form.isGone)
    }

    private companion object {
        const val AWAIT_MILLIS = 5_000L
        const val SETTLE_MILLIS = 500L
    }
}
