package kr.chagok.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 장소 한 점 [TripPlace] */
class TripPlaceTest {

    @Test
    fun `내비 목적지처럼 이름이 있으면 이름을 보여 준다`() {
        val place = TripPlace(name = "○○전자 판교사옥", address = "판교역로 1")

        assertTrue(place.hasName)
        assertEquals("○○전자 판교사옥", place.displayName)
    }

    @Test
    fun `이름 없이 주소만 알면 주소를 보여 준다`() {
        val place = TripPlace(address = "분당구 백현동")

        assertFalse(place.hasName)
        assertEquals("분당구 백현동", place.displayName)
    }

    @Test
    fun `공백뿐인 이름은 이름이 없는 것으로 본다`() {
        val place = TripPlace(name = "  ", address = "분당구 백현동")

        assertFalse(place.hasName)
        assertEquals("분당구 백현동", place.displayName)
    }
}
