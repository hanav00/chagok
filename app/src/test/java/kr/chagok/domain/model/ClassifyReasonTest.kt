package kr.chagok.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 분류 근거 [ClassifyReason] */
class ClassifyReasonTest {

    @Test
    fun `출발 전 선택·사용자 수정·관리자 수정은 사람이 정한 분류`() {
        assertTrue(ClassifyReason.PRE_PICK.isChangedByUser)
        assertTrue(ClassifyReason.USER_EDIT.isChangedByUser)
        assertTrue(ClassifyReason.ADMIN_EDIT.isChangedByUser)
    }

    @Test
    fun `기본값 그대로인 운행은 사람이 정한 분류가 아니다`() {
        assertFalse(ClassifyReason.DEFAULT_BUSINESS.isChangedByUser)
        assertFalse(ClassifyReason.DEFAULT_PERSONAL.isChangedByUser)
    }

    @Test
    fun `빠진 주행을 고른 것(사용자 선택)은 방금 운행 카드에 근거를 덧붙이지 않는다`() {
        assertFalse(ClassifyReason.USER_PICK.isChangedByUser)
    }

    @Test
    fun `기본값 근거는 개인이면 기본값 개인, 업무·출퇴근이면 기본값 업무`() {
        assertEquals(ClassifyReason.DEFAULT_PERSONAL, ClassifyReason.defaultOf(Category.PERSONAL))
        assertEquals(ClassifyReason.DEFAULT_BUSINESS, ClassifyReason.defaultOf(Category.BUSINESS))
        assertEquals(ClassifyReason.DEFAULT_BUSINESS, ClassifyReason.defaultOf(Category.COMMUTE))
    }
}
