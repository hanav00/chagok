package kr.chagok.domain

import kr.chagok.domain.model.Category
import org.junit.Assert.assertEquals
import org.junit.Test

/** 마이크 한마디 나누기 [SpokenEdit.parse] */
class SpokenEditTest {

    @Test
    fun `정해 둔 분류 말은 띄어쓰기·끝말·문장부호와 상관없이 분류를 바꾼다`() {
        listOf("개인", "개인 용무였어", "개인 운행이에요.", "개인으로 바꿔 줘", "개인이요").forEach {
            assertEquals(it, SpokenEdit.Classify(Category.PERSONAL), SpokenEdit.parse(it))
        }
        listOf("업무로요", "업무였어", "일반 업무").forEach {
            assertEquals(it, SpokenEdit.Classify(Category.BUSINESS), SpokenEdit.parse(it))
        }
        assertEquals(SpokenEdit.Classify(Category.COMMUTE), SpokenEdit.parse("출퇴근이었어"))
    }

    @Test
    fun `분류 말이 섞인 긴 말은 방문 목적으로 남는다`() {
        assertEquals(SpokenEdit.Note("개인 고객 미팅"), SpokenEdit.parse(" 개인 고객 미팅 "))
        assertEquals(SpokenEdit.Note("업무 협의"), SpokenEdit.parse("업무 협의"))
        assertEquals(SpokenEdit.Note("신제품 미팅"), SpokenEdit.parse("신제품 미팅"))
    }

    @Test
    fun `탔어·운전했어로 끝나는 말은 사람 지정이고, 조사를 뗀 이름도 후보로 둔다`() {
        assertEquals(
            SpokenEdit.AssignPerson(listOf("박정훈이", "박정훈"), "박정훈이 탔어"),
            SpokenEdit.parse("박정훈이 탔어"),
        )
        assertEquals(
            SpokenEdit.AssignPerson(listOf("최서연"), "최서연 운전했어요"),
            SpokenEdit.parse("최서연 운전했어요"),
        )
        assertEquals(SpokenEdit.Note("운전 조심하라고 함"), SpokenEdit.parse("운전 조심하라고 함"))
    }
}
