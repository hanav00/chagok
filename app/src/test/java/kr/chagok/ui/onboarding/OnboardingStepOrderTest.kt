package kr.chagok.ui.onboarding

import kr.chagok.domain.model.VehicleUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 첫 실행 튜토리얼 단계 순서:
 * 시작 → 누가 쓰나요 → … → 집·회사 → 이렇게 기록돼요 → 말해 보기 → 정리.
 * 말해 보기는 Gleo 가 없는 차도 거친다 ("1탭으로 바꿔 보기").
 */
class OnboardingStepOrderTest {

    @Test
    fun `처음은 시작 화면이고 이전이 없다, 다음은 누가 쓰나요`() {
        val welcome = OnboardingUiState()

        assertEquals(OnboardingStep.WELCOME, welcome.step)
        assertNull(welcome.previousStep)
        assertEquals(OnboardingStep.WHO, welcome.nextStep)
        assertEquals(OnboardingStep.WELCOME, welcome.copy(step = OnboardingStep.WHO).previousStep)
    }

    @Test
    fun `집·회사 → 이렇게 기록돼요 → 말해 보기 → 정리, 이전으로는 거꾸로`() {
        val places = OnboardingUiState(step = OnboardingStep.PLACES, usage = VehicleUsage.SOLO)

        assertEquals(OnboardingStep.HOW, places.nextStep)
        assertEquals(OnboardingStep.TRY, places.copy(step = OnboardingStep.HOW).nextStep)
        assertEquals(OnboardingStep.DONE, places.copy(step = OnboardingStep.TRY).nextStep)
        assertEquals(OnboardingStep.TRY, places.copy(step = OnboardingStep.DONE).previousStep)
        assertEquals(OnboardingStep.HOW, places.copy(step = OnboardingStep.TRY).previousStep)
        assertEquals(OnboardingStep.PLACES, places.copy(step = OnboardingStep.HOW).previousStep)
    }

    @Test
    fun `Gleo 가 없는 차도 말해 보기 자리를 거친다 (1탭으로 바꿔 보기)`() {
        val places = OnboardingUiState(step = OnboardingStep.PLACES, hasGleo = false)

        assertEquals(OnboardingStep.HOW, places.nextStep)
        assertEquals(OnboardingStep.DONE, places.copy(step = OnboardingStep.TRY).nextStep)
    }

    @Test
    fun `건너뛰어 온 정리 화면의 이전은 누가 쓰나요`() {
        val skipped = OnboardingUiState(step = OnboardingStep.DONE, isSkipped = true)

        assertEquals(OnboardingStep.WHO, skipped.previousStep)
    }
}
