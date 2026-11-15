package kr.chagok.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 다음 출발의 분류·사람 [NextDeparture.resolve] (홈 "다음 출발" 토글 · "누가 타나요?") */
class NextDepartureTest {
    private val people = listOf(Person(1, "박정훈", initial = "박"), Person(2, "최서연", initial = "최"))

    private fun resolve(
        defaultCategory: Category = Category.BUSINESS,
        usage: VehicleUsage = VehicleUsage.SOLO,
        people: List<Person> = this.people,
        pickedCategory: Category? = null,
        pickedPersonId: Long? = null,
    ) = NextDeparture.resolve(defaultCategory, usage, people, pickedCategory, pickedPersonId)

    @Test
    fun `아무것도 안 고르면 기본값 그대로, 바꾼 것이 아니다`() {
        val next = resolve(defaultCategory = Category.PERSONAL)

        assertEquals(Category.PERSONAL, next.category)
        assertFalse(next.isChanged)
    }

    @Test
    fun `기본값과 같은 쪽을 고르면 바꾼 것이 아니다`() {
        val next = resolve(defaultCategory = Category.PERSONAL, pickedCategory = Category.PERSONAL)

        assertFalse(next.isChanged)
    }

    @Test
    fun `기본값 업무에서 개인을 고르면 개인, 바꾼 것으로 남는다`() {
        val next = resolve(defaultCategory = Category.BUSINESS, pickedCategory = Category.PERSONAL)

        assertEquals(Category.PERSONAL, next.category)
        assertTrue(next.isChanged)
    }

    @Test
    fun `기본값 개인에서 업무를 고르면 업무, 바꾼 것으로 남는다`() {
        val next = resolve(defaultCategory = Category.PERSONAL, pickedCategory = Category.BUSINESS)

        assertEquals(Category.BUSINESS, next.category)
        assertTrue(next.isChanged)
    }

    @Test
    fun `혼자 쓰는 차는 다른 사람을 골라도 늘 첫 사람`() {
        assertEquals(1L, resolve(usage = VehicleUsage.SOLO, pickedPersonId = 2).personId)
        assertEquals(1L, resolve(usage = VehicleUsage.SOLO, pickedPersonId = null).personId)
    }

    @Test
    fun `혼자 쓰는 차인데 등록한 사람이 없으면 사람은 null`() {
        assertNull(resolve(usage = VehicleUsage.SOLO, people = emptyList()).personId)
    }

    @Test
    fun `공용차는 고른 사람, 안 고르면 미지정(null)`() {
        assertEquals(2L, resolve(usage = VehicleUsage.SHARED, pickedPersonId = 2).personId)
        assertNull(resolve(usage = VehicleUsage.SHARED, pickedPersonId = null).personId)
    }

    @Test
    fun `기사 차도 공용차처럼 고른 사람`() {
        assertEquals(2L, resolve(usage = VehicleUsage.CHAUFFEUR, pickedPersonId = 2).personId)
        assertNull(resolve(usage = VehicleUsage.CHAUFFEUR, pickedPersonId = null).personId)
    }

    @Test
    fun `공용차에서 고른 사람이 그사이 숨겨지거나 지워져 명단에 없으면 미지정(null)`() {
        assertNull(resolve(usage = VehicleUsage.SHARED, pickedPersonId = 3).personId)
    }
}
