package kr.chagok.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 사람 [Person]: 이름 없이 등록한 사람은 빈 이름이고, 얼굴 원은 번호나 "나" */
class PersonTest {
    @Test
    fun `이름이 비었거나 빈칸뿐이면 이름 없는 사람`() {
        assertFalse(Person(1, "", initial = "2").isNamed)
        assertFalse(Person(1, "  ", initial = "2").isNamed)
        assertTrue(Person(1, "박정훈", initial = "박").isNamed)
    }

    @Test
    fun `얼굴 원은 이름 첫 글자, 앞뒤 빈칸은 빼고 본다`() {
        assertEquals("박", Person.initialOf(" 박정훈", unnamed = "1"))
    }

    @Test
    fun `이름이 없으면 얼굴 원은 넘겨 준 글자 (등록 번호나 '나')`() {
        assertEquals("3", Person.initialOf("", unnamed = "3"))
        assertEquals("나", Person.initialOf(" ", unnamed = "나"))
    }
}
