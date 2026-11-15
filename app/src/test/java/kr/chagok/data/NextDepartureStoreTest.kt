package kr.chagok.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.ChagokSettings
import kr.chagok.domain.model.NextDeparture
import kr.chagok.domain.model.Person
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.testing.TestApp.Companion.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 홈 "다음 출발 [업무|개인]" 토글과 "누가 타나요?".
 * 고른 값이 없으면 기본값, 운행이 끝나면 다시 기본값으로. 안드로이드 없이 돈다.
 */
class NextDepartureStoreTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settings =
        MutableStateFlow(ChagokSettings(defaultCategory = Category.BUSINESS, usage = VehicleUsage.SOLO))
    private val people = MutableStateFlow(
        listOf(Person(1, "박정훈", initial = "박"), Person(2, "최서연", initial = "최")),
    )
    private val store = NextDepartureStore(settings, people, scope)

    @After
    fun tearDown() = scope.cancel()

    private suspend fun nextWhere(condition: (NextDeparture) -> Boolean): NextDeparture =
        requireNotNull(await(store.next) { it != null && condition(it) })

    @Test
    fun `설정을 읽기 전에는 다음 출발을 정하지 않는다`() {
        val notLoaded = NextDepartureStore(MutableSharedFlow(), people, scope)

        assertNull(notLoaded.next.value)
    }

    @Test
    fun `아무것도 고르지 않으면 기본 분류를 따르고 바꾼 것이 아니다`() = runBlocking {
        val next = nextWhere { true }

        assertEquals(NextDeparture(Category.BUSINESS, isChanged = false, personId = 1), next)
    }

    @Test
    fun `설정에서 기본 분류를 바꾸면 다음 출발도 따라 바뀐다`() = runBlocking {
        nextWhere { it.category == Category.BUSINESS }

        settings.value = settings.value.copy(defaultCategory = Category.PERSONAL)

        assertEquals(
            NextDeparture(Category.PERSONAL, isChanged = false, personId = 1),
            nextWhere { it.category == Category.PERSONAL },
        )
    }

    @Test
    fun `기본값과 다른 쪽을 고르면 바꾼 것(출발 전 선택)이다`() = runBlocking {
        store.pickCategory(Category.PERSONAL)

        val next = nextWhere { it.category == Category.PERSONAL }

        assertEquals(true, next.isChanged)
    }

    @Test
    fun `기본값과 같은 쪽을 고르면 바꾼 것이 아니다`() = runBlocking {
        store.pickCategory(Category.PERSONAL)
        nextWhere { it.isChanged }

        store.pickCategory(Category.BUSINESS)

        assertEquals(NextDeparture(Category.BUSINESS, isChanged = false, personId = 1), nextWhere { !it.isChanged })
    }

    @Test
    fun `혼자 쓰는 차는 다른 사람을 골라도 늘 첫 사람이다`() = runBlocking {
        store.pickPerson(2)
        store.pickCategory(Category.PERSONAL) // 고른 값이 반영된 뒤를 보려고 분류도 바꾼다

        assertEquals(1L, nextWhere { it.category == Category.PERSONAL }.personId)
    }

    @Test
    fun `혼자 쓰는 차에 사람이 아직 없으면 사람은 미지정이다`() = runBlocking {
        people.value = emptyList()

        assertNull(nextWhere { it.personId == null }.personId)
    }

    @Test
    fun `공용차는 고르기 전에는 미지정이고, 고르면 그 사람이다`() = runBlocking {
        settings.value = settings.value.copy(usage = VehicleUsage.SHARED)
        assertNull(nextWhere { it.personId == null }.personId)

        store.pickPerson(2)

        assertEquals(2L, nextWhere { it.personId != null }.personId)
    }

    @Test
    fun `운행이 끝나 비우면 분류는 기본값으로, 공용차의 사람은 미지정으로 돌아간다`() = runBlocking {
        settings.value = settings.value.copy(usage = VehicleUsage.SHARED)
        store.pickCategory(Category.PERSONAL)
        store.pickPerson(2)
        nextWhere { it.category == Category.PERSONAL && it.personId == 2L }

        store.clear()

        assertEquals(
            NextDeparture(Category.BUSINESS, isChanged = false, personId = null),
            nextWhere { it.category == Category.BUSINESS },
        )
    }
}
