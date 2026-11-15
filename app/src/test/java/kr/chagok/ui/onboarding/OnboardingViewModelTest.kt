package kr.chagok.ui.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kr.chagok.R
import kr.chagok.car.NaviBookmarks
import kr.chagok.car.VoiceInput
import kr.chagok.domain.ChagokConfig
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.TripPlace
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.testing.ScriptedVoiceInput
import kr.chagok.testing.TestApp.Companion.await
import kr.chagok.testing.TripFixtures
import kr.chagok.testing.ViewModelTestRule
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
 * 첫 실행 화면을 누르는 순서대로: 단계 이동, 사람 등록, 집·회사 고르기, 건너뛰기, 시작하기 저장.
 * 필수 입력은 없다. 단계 순서만 보는 테스트는 [OnboardingStepOrderTest].
 */
@RunWith(RobolectricTestRunner::class)
class OnboardingViewModelTest {
    @get:Rule
    val rule = ViewModelTestRule()
    private val app get() = rule.app

    /** 화면이 쓰는 이름표 그대로 ("나", "이름 없음 2") */
    private val labels: UnnamedLabels = ApplicationProvider.getApplicationContext<Context>().let { context ->
        UnnamedLabels(context.getString(R.string.onboarding_me)) {
            context.getString(R.string.person_unnamed, it.toString())
        }
    }

    private val herePlace = TripPlace(address = "성남시 분당구 판교역로 235", latitude = 37.40, longitude = 127.10)

    /** 첫 실행 화면을 연 채로 [block] 을 돌린다. 화면이 떠 있는 동안처럼 상태를 늘 구독해 둔다 */
    private fun onboarding(block: suspend Screen.() -> Unit) = runBlocking<Unit> {
        val viewModel = rule.viewModel { OnboardingViewModel(app.container) }
        val shown = launch { viewModel.state.collect {} }
        try {
            val screen = Screen(viewModel)
            // 시작 화면("시작하기")을 지나 누가 쓰나요부터
            screen.press({ next() }) { it.step == OnboardingStep.WHO }
            screen.block()
        } finally {
            shown.cancel()
        }
    }

    /**
     * 첫 실행 화면. 사용자는 바뀐 화면을 보고 다음을 누르므로, 누를 때마다 화면이 바뀌기를 기다린다
     * ("다음"·"추가"는 지금 화면 상태로 누를 수 있는지 정한다).
     */
    private inner class Screen(val vm: OnboardingViewModel) {
        /** 메인 스레드에서 누르고, 화면이 [shows] 가 될 때까지 기다린다 */
        suspend fun press(
            action: OnboardingViewModel.() -> Unit,
            shows: (OnboardingUiState) -> Boolean,
        ): OnboardingUiState {
            rule.onMain { vm.action() }
            return until(shows)
        }

        /** 아무 일도 없어야 하는 누름. 뒤에 다른 누름을 하고 그 화면에서 그대로인지 본다 */
        suspend fun pressIgnored(action: OnboardingViewModel.() -> Unit) = rule.onMain { vm.action() }

        suspend fun until(shows: (OnboardingUiState) -> Boolean): OnboardingUiState = await(vm.state, shows)

        suspend fun current(): OnboardingUiState = until { true }
    }

    @Test
    fun `처음 → 누가 쓰나요, 나 혼자 써요가 미리 골라져 있어 바로 다음으로 간다`() = onboarding {
        val state = current()
        assertEquals(OnboardingStep.WHO, state.step)
        assertEquals(VehicleUsage.SOLO, state.usage)
        assertTrue(state.isNextEnabled)
        assertEquals("이전은 시작 화면", OnboardingStep.WELCOME, state.previousStep)

        val after = press({ next() }) { it.step != OnboardingStep.WHO }
        assertEquals("혼자 쓰는 차는 사람·관리자를 건너뛴다", OnboardingStep.MODE, after.step)
    }

    @Test
    fun `혼자 쓰는 차 → 사람·관리자 단계를 건너뛰고 기본 분류로, 이전은 다시 누가 쓰나요`() = onboarding {
        press({ selectUsage(VehicleUsage.SOLO) }) { it.isNextEnabled }
        press({ next() }) { it.step == OnboardingStep.MODE }

        press({ previous() }) { it.step == OnboardingStep.WHO }
    }

    @Test
    fun `다음을 두 번 누르면 두 번째는 이미 지난 단계라 무시한다 (한 단계를 건너뛰지 않는다)`() = onboarding {
        press({ selectUsage(VehicleUsage.SOLO) }) { it.isNextEnabled }

        press({ next(from = OnboardingStep.WHO) }) { it.step == OnboardingStep.MODE }

        pressIgnored { next(from = OnboardingStep.WHO) } // 화면이 바뀌기 전에 눌린 두 번째
        val after = press({ selectDefaultCategory(Category.PERSONAL) }) { it.defaultCategory == Category.PERSONAL }
        assertEquals(OnboardingStep.MODE, after.step)
    }

    @Test
    fun `여러 명이 쓰는 차 → 모든 단계를 거쳐 정리, 정리 화면은 다음 대신 시작하기`() = onboarding {
        press({ selectUsage(VehicleUsage.SHARED) }) { it.isNextEnabled }
        press({ next() }) { it.step == OnboardingStep.PEOPLE }
        press({ addUnnamedPerson() }) { it.isNextEnabled }
        press({ next() }) { it.step == OnboardingStep.ADMIN }
        press({ next() }) { it.step == OnboardingStep.MODE }
        val back = press({ previous() }) { it.step != OnboardingStep.MODE }
        assertEquals("여러 명이면 이전은 관리자", OnboardingStep.ADMIN, back.step)
        press({ next() }) { it.step == OnboardingStep.MODE }
        press({ next() }) { it.step == OnboardingStep.PLACES }

        press({ next() }) { it.step == OnboardingStep.HOW }
        press({ next() }) { it.step == OnboardingStep.TRY }
        val done = press({ next() }) { it.step == OnboardingStep.DONE }
        assertNull(done.nextStep)
        assertEquals(OnboardingStep.TRY, done.previousStep)
    }

    @Test
    fun `사람 등록 → 2명이 되어야 다음, 4명까지만`() = onboarding {
        press({ selectUsage(VehicleUsage.CHAUFFEUR) }) { it.isNextEnabled }
        val one = press({ next() }) { it.step == OnboardingStep.PEOPLE }
        assertEquals(1, one.people.size)
        assertFalse("최소 ${ChagokConfig.PEOPLE_MIN}명", one.isNextEnabled)

        press({ addUnnamedPerson() }) { it.people.size == 2 && it.isNextEnabled }
        press({ addUnnamedPerson() }) { it.people.size == 3 }
        val full = press({ addUnnamedPerson() }) { it.people.size == ChagokConfig.PEOPLE_MAX }
        assertFalse(full.canAddPerson)

        pressIgnored { addUnnamedPerson() }
        val after = press({ selectDefaultCategory(Category.PERSONAL) }) { it.defaultCategory == Category.PERSONAL }
        assertEquals(ChagokConfig.PEOPLE_MAX, after.people.size)
    }

    @Test
    fun `이름 없이 추가한 사람 → 나, 이름 없음 2 · 얼굴 원은 나, 2`() = onboarding {
        press({ selectUsage(VehicleUsage.SHARED) }) { it.isNextEnabled }
        press({ next() }) { it.step == OnboardingStep.PEOPLE }

        val state = press({ addUnnamedPerson() }) { it.people.size == 2 }

        assertEquals("나", state.personTitle(0, labels))
        assertEquals("이름 없음 2", state.personTitle(1, labels))
        assertEquals("나", state.personInitial(0, labels))
        assertEquals("2", state.personInitial(1, labels))
    }

    @Test
    fun `마이크로 이름을 말함 → 그 이름으로 추가, 얼굴 원은 첫 글자`() = run {
        app.voiceInput = ScriptedVoiceInput("김민수")
        onboarding {
            press({ selectUsage(VehicleUsage.SHARED) }) { it.isNextEnabled }
            press({ next() }) { it.step == OnboardingStep.PEOPLE }

            val state = press({ addPersonByVoice() }) { !it.isListening && it.people.size == 2 }

            assertEquals("김민수", state.personTitle(1, labels))
            assertEquals("김", state.personInitial(1, labels))
        }
    }

    @Test
    fun `마이크로 못 알아들음 → 이름 없는 사람으로 남는다 (이름 없음 2)`() = run {
        app.voiceInput = ScriptedVoiceInput(null)
        onboarding {
            press({ selectUsage(VehicleUsage.SHARED) }) { it.isNextEnabled }
            press({ next() }) { it.step == OnboardingStep.PEOPLE }

            val state = press({ addPersonByVoice() }) { !it.isListening && it.people.size == 2 }

            assertEquals("", state.people[1].name)
            assertEquals("이름 없음 2", state.personTitle(1, labels))
        }
    }

    @Test
    fun `마이크로 듣는 중에 또 누르면 무시한다 (빈 사람이 더 생기지 않는다)`() = run {
        val spoken = CompletableDeferred<String?>()
        app.voiceInput = object : VoiceInput {
            override suspend fun listen(): String? = spoken.await()
            override fun release() = Unit
        }
        onboarding {
            press({ selectUsage(VehicleUsage.SHARED) }) { it.isNextEnabled }
            press({ next() }) { it.step == OnboardingStep.PEOPLE }
            press({ addPersonByVoice() }) { it.isListening }

            pressIgnored { addPersonByVoice() }
            spoken.complete("김민수")

            val state = until { !it.isListening }
            assertEquals(listOf("", "김민수"), state.people.map { it.name })
        }
    }

    @Test
    fun `내비 즐겨찾기가 없다 → 집·회사는 안 씀, 즐겨찾기가 맞아요는 고를 수 없다`() = onboarding {
        val state = current()
        assertEquals(PlaceMode.SKIP, state.home.mode)
        assertEquals(PlaceMode.SKIP, state.office.mode)
        assertFalse(state.home.canPick(PlaceMode.BOOKMARK))

        pressIgnored { selectPlace(PlaceKind.HOME, PlaceMode.BOOKMARK) }
        val after = press({ selectDefaultCategory(Category.PERSONAL) }) { it.defaultCategory == Category.PERSONAL }
        assertEquals(PlaceMode.SKIP, after.home.mode)
    }

    @Test
    fun `내비 즐겨찾기 집·회사가 있다 → 미리 즐겨찾기가 맞아요`() = run {
        app.navi.bookmarks.value = NaviBookmarks(home = TripFixtures.HOME, office = TripFixtures.OFFICE)
        onboarding {
            val state = until { it.home.hasBookmark }
            assertEquals(PlaceMode.BOOKMARK, state.home.mode)
            assertEquals(PlaceMode.BOOKMARK, state.office.mode)
        }
    }

    @Test
    fun `집을 지금 여기로 → 회사는 지금 여기를 고를 수 없다 (한 자리에 둘 다 있을 수 없다)`() = run {
        app.navi.location = herePlace
        onboarding {
            val state = press({ selectPlace(PlaceKind.HOME, PlaceMode.HERE) }) { it.herePlace != null }
            assertEquals(PlaceMode.HERE, state.home.mode)
            assertEquals(herePlace, state.herePlace)
            assertTrue(state.office.isHereTaken)
            assertFalse(state.office.canPick(PlaceMode.HERE))

            pressIgnored { selectPlace(PlaceKind.OFFICE, PlaceMode.HERE) }
            val after = press({ selectDefaultCategory(Category.PERSONAL) }) { it.defaultCategory == Category.PERSONAL }
            assertEquals(PlaceMode.SKIP, after.office.mode)

            val freed = press({ selectPlace(PlaceKind.HOME, PlaceMode.SKIP) }) { it.home.mode == PlaceMode.SKIP }
            assertTrue("집을 풀면 회사가 지금 여기를 고를 수 있다", freed.office.canPick(PlaceMode.HERE))
        }
    }

    @Test
    fun `지금 여기 위치를 읽는 동안은 다음으로 못 간다 (읽기 전에 끝내면 집·회사가 빠진다)`() = run {
        val located = CompletableDeferred<Unit>()
        app.navi.location = herePlace
        app.navi.whileLocating = { located.await() }
        onboarding {
            press({ selectUsage(VehicleUsage.SOLO) }) { it.isNextEnabled }
            press({ next() }) { it.step == OnboardingStep.MODE }
            press({ next() }) { it.step == OnboardingStep.PLACES }

            val loading = press({ selectPlace(PlaceKind.HOME, PlaceMode.HERE) }) { it.isHereLoading }
            assertFalse(loading.isNextEnabled)

            located.complete(Unit)
            val loaded = until { !it.isHereLoading }
            assertEquals(herePlace, loaded.herePlace)
            assertTrue(loaded.isNextEnabled)
        }
    }

    @Test
    fun `건너뛰기 → 정리 화면, 업무·혼자 쓰는 차, 앞에서 정한 관리자 PIN 은 지우지 않는다`() = run {
        runBlocking { app.setUpUnlockedAdmin() } // 관리자 단계에서 PIN 을 정해 둔 뒤 처음으로 돌아왔다
        onboarding {
            until { it.hasPin }
            press({ selectUsage(VehicleUsage.SHARED) }) { it.usage == VehicleUsage.SHARED }
            press({ selectDefaultCategory(Category.PERSONAL) }) { it.defaultCategory == Category.PERSONAL }

            // 적어 둔 복구키까지 몰래 무효가 되지 않게 PIN 은 남긴다 (정리 화면에 관리자 줄로 보인다)
            val state = press({ skip() }) { it.isSkipped && it.hasPin }

            assertEquals(OnboardingStep.DONE, state.step)
            assertEquals(OnboardingStep.WHO, state.previousStep)
            val result = state.toChoice(labels).toResult()
            assertEquals(VehicleUsage.SOLO, result.usage)
            assertEquals(Category.BUSINESS, result.defaultCategory)
            assertNull(result.home)
            assertEquals("이름표 \"나\"는 화면에만, 이름은 비워 둔다", listOf(""), result.people.map { it.name })
            assertEquals(listOf("나"), result.people.map { it.initial })
        }
    }

    @Test
    fun `건너뛰어 정리 화면에 왔다가 이전 → 다시 하나씩 고르는 중 (건너뜀이 풀린다)`() = onboarding {
        press({ skip() }) { it.isSkipped }

        val state = press({ previous() }) { it.step == OnboardingStep.WHO }

        assertFalse(state.isSkipped)
    }

    @Test
    fun `여러 명·개인 기본·즐겨찾기 집·회사로 시작하기 → 설정과 사람이 저장된다`() = run {
        app.voiceInput = ScriptedVoiceInput("김민수")
        app.navi.bookmarks.value = NaviBookmarks(home = TripFixtures.HOME, office = TripFixtures.OFFICE)
        onboarding {
            press({ selectUsage(VehicleUsage.SHARED) }) { it.isNextEnabled }
            press({ next() }) { it.step == OnboardingStep.PEOPLE }
            press({ addPersonByVoice() }) { !it.isListening && it.people.size == 2 }
            press({ addUnnamedPerson() }) { it.people.size == 3 }
            press({ next() }) { it.step == OnboardingStep.ADMIN }
            press({ next() }) { it.step == OnboardingStep.MODE }
            press({ selectDefaultCategory(Category.PERSONAL) }) { it.defaultCategory == Category.PERSONAL }
            press({ next() }) { it.step == OnboardingStep.PLACES }
            press({ next() }) { it.step == OnboardingStep.HOW }
            press({ next() }) { it.step == OnboardingStep.TRY }
            press({ next() }) { it.step == OnboardingStep.DONE && it.home.hasBookmark }

            rule.onMain { vm.finish(labels) }

            val saved = await(app.container.settings.settings) { it.isFirstRunDone }
            assertEquals(VehicleUsage.SHARED, saved.usage)
            assertEquals(Category.PERSONAL, saved.defaultCategory)
            assertEquals(TripFixtures.HOME, saved.home)
            assertEquals(TripFixtures.OFFICE, saved.office)
            val people = await(app.container.people.people) { it.size == 3 }
            // 이름 없는 사람은 빈 이름으로 저장한다 ("나"·"이름 없음 3"이 서식 ④성명에 찍히지 않게)
            assertEquals(listOf("", "김민수", ""), people.map { it.name })
            assertEquals(listOf("나", "김", "3"), people.map { it.initial })
        }
    }

    @Test
    fun `혼자 쓰는 차로 시작하기 → 첫 사람만, 집만 있고 회사가 없으면 집·회사 둘 다 안 쓴다`() = run {
        app.navi.bookmarks.value = NaviBookmarks(home = TripFixtures.HOME)
        onboarding {
            press({ selectUsage(VehicleUsage.SOLO) }) { it.isNextEnabled }
            press({ next() }) { it.step == OnboardingStep.MODE }
            press({ next() }) { it.step == OnboardingStep.PLACES }
            press({ next() }) { it.step == OnboardingStep.HOW }
            press({ next() }) { it.step == OnboardingStep.TRY }
            press({ next() }) { it.step == OnboardingStep.DONE && it.home.hasBookmark }

            rule.onMain { vm.finish(labels) }

            val saved = await(app.container.settings.settings) { it.isFirstRunDone }
            assertEquals(VehicleUsage.SOLO, saved.usage)
            assertEquals(Category.BUSINESS, saved.defaultCategory)
            assertNull("집↔회사도 일반 업무로 적는다", saved.home)
            assertNull(saved.office)
            val me = await(app.container.people.people) { it.isNotEmpty() }.single()
            assertEquals("이름 없는 \"나\"는 빈 이름으로, 얼굴 원만 \"나\"", "" to "나", me.name to me.initial)
        }
    }
}
