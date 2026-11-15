package kr.chagok.ui.onboarding

import kr.chagok.domain.model.VehicleUsage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 첫 실행 상태 [OnboardingUiState]: 이름 없이 넣은 사람.
 * 이름표 "나"·"이름 없음 n"은 화면에만 쓰고, 서식 ④성명에 찍히지 않게 빈 이름으로 저장한다.
 */
class OnboardingStateTest {
    private val labels = UnnamedLabels(me = "나") { number -> "이름 없음 $number" }
    private val state = OnboardingUiState(
        usage = VehicleUsage.SHARED,
        people = listOf(DraftPerson(), DraftPerson("김지은 "), DraftPerson()),
    )

    @Test
    fun `화면에는 이름 없는 사람이 '나'·'이름 없음 n'으로 보인다`() {
        assertEquals(listOf("나", "김지은 ", "이름 없음 3"), state.people.indices.map { state.personTitle(it, labels) })
    }

    @Test
    fun `저장할 값은 이름 없는 사람을 빈 이름으로 두고, 얼굴 원은 '나'·번호다`() {
        val people = state.toChoice(labels).people

        assertEquals(listOf("", "김지은", ""), people.map { it.name })
        assertEquals(listOf("나", "김", "3"), people.map { it.initial })
    }
}
