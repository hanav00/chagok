package kr.chagok.domain

import kr.chagok.domain.model.Category
import kr.chagok.domain.model.TripPlace
import kr.chagok.domain.model.VehicleUsage
import kr.chagok.testing.TripFixtures.HOME
import kr.chagok.testing.TripFixtures.OFFICE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 첫 실행에서 고른 값 → 실제로 적용될 값 [FirstRunChoice.toResult] */
class FirstRunChoiceTest {
    private val me = NewPerson("박정훈", "나", "대표이사")
    private val second = NewPerson("최서연", "최", "영업팀")
    private val third = NewPerson("", "3")

    private fun choice(
        isSkipped: Boolean = false,
        usage: VehicleUsage? = VehicleUsage.SHARED,
        defaultCategory: Category = Category.PERSONAL,
        home: TripPlace? = HOME,
        office: TripPlace? = OFFICE,
        people: List<NewPerson> = listOf(me, second, third),
    ) = FirstRunChoice(isSkipped, usage, defaultCategory, home, office, people)

    @Test
    fun `나중에로 건너뛰면 고른 값과 상관없이 혼자 쓰는 차 · 업무 · 집·회사 없음 · 첫 사람만`() {
        val result = choice(isSkipped = true).toResult()

        assertEquals(VehicleUsage.SOLO, result.usage)
        assertEquals(Category.BUSINESS, result.defaultCategory)
        assertNull(result.home)
        assertNull(result.office)
        assertEquals(listOf(me), result.people)
    }

    @Test
    fun `건너뛰었는데 등록한 사람이 없으면 사람도 없다`() {
        assertEquals(emptyList<NewPerson>(), choice(isSkipped = true, people = emptyList()).toResult().people)
    }

    @Test
    fun `누가 쓰는 차인지 안 고르면 혼자 쓰는 차`() {
        assertEquals(VehicleUsage.SOLO, choice(usage = null).toResult().usage)
    }

    @Test
    fun `혼자 쓰는 차는 첫 사람만 남긴다`() {
        val result = choice(usage = VehicleUsage.SOLO).toResult()

        assertEquals(listOf(me), result.people)
    }

    @Test
    fun `공용차·기사 차는 등록한 사람을 순서대로 모두 남긴다`() {
        assertEquals(listOf(me, second, third), choice(usage = VehicleUsage.SHARED).toResult().people)
        assertEquals(listOf(me, second, third), choice(usage = VehicleUsage.CHAUFFEUR).toResult().people)
    }

    @Test
    fun `건너뛰지 않으면 고른 기본 분류를 그대로 쓴다`() {
        assertEquals(Category.PERSONAL, choice(defaultCategory = Category.PERSONAL).toResult().defaultCategory)
    }

    @Test
    fun `집과 회사를 모두 정하면 둘 다 남는다`() {
        val result = choice().toResult()

        assertEquals(HOME, result.home)
        assertEquals(OFFICE, result.office)
    }

    @Test
    fun `집만 정하고 회사가 없으면 둘 다 버린다 (출퇴근을 나눌 수 없음)`() {
        val result = choice(home = HOME, office = null).toResult()

        assertNull(result.home)
        assertNull(result.office)
    }

    @Test
    fun `회사만 정하고 집이 없어도 둘 다 버린다`() {
        val result = choice(home = null, office = OFFICE).toResult()

        assertNull(result.home)
        assertNull(result.office)
    }
}
