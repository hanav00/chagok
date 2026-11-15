package kr.chagok.domain.model

import kr.chagok.testing.TripFixtures.CLIENT
import kr.chagok.testing.TripFixtures.OFFICE
import kr.chagok.testing.TripFixtures.trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 운행 한 건 [Trip] = 국세청 운행기록부 한 줄: 거리, 서식 칸, 분류 바꾸기 */
class TripTest {
    private val delta = 1e-6

    @Test
    fun `주행거리는 도착 계기판 − 출발 계기판 (⑦ = ⑥ − ⑤)`() {
        assertEquals(9.2, trip(startKm = 23_409.0, endKm = 23_418.2).km, delta)
    }

    @Test
    fun `출퇴근 운행은 ⑧출·퇴근 칸에만 거리가 들어간다`() {
        val form = trip(startKm = 100.0, endKm = 109.6, category = Category.COMMUTE).formDistances()

        assertEquals(100.0, form.startKm, 0.0)
        assertEquals(109.6, form.endKm, 0.0)
        assertEquals(9.6, form.km, delta)
        assertEquals(9.6, form.commuteKm, delta)
        assertEquals(0.0, form.businessKm, 0.0)
    }

    @Test
    fun `일반 업무 운행은 ⑨일반 업무 칸에만 거리가 들어간다`() {
        val form = trip(startKm = 100.0, endKm = 104.2, category = Category.BUSINESS).formDistances()

        assertEquals(0.0, form.commuteKm, 0.0)
        assertEquals(4.2, form.businessKm, delta)
    }

    @Test
    fun `개인 운행은 ⑤⑥⑦만 있고 ⑧⑨는 비어 있다`() {
        val form = trip(startKm = 100.0, endKm = 103.8, category = Category.PERSONAL).formDistances()

        assertEquals(3.8, form.km, delta)
        assertEquals(0.0, form.commuteKm, 0.0)
        assertEquals(0.0, form.businessKm, 0.0)
    }

    @Test
    fun `분류 대기(빠진 주행)도 ⑧⑨는 비어 있다`() {
        val form = trip(startKm = 100.0, endKm = 108.2, category = null, isGap = true, personId = null).formDistances()

        assertEquals(8.2, form.km, delta)
        assertEquals(0.0, form.commuteKm, 0.0)
        assertEquals(0.0, form.businessKm, 0.0)
    }

    @Test
    fun `개인으로 바꾸면 장소를 지운다`() {
        val business = trip(category = Category.BUSINESS)

        val personal = business.reclassified(Category.PERSONAL, ClassifyReason.USER_EDIT)

        assertEquals(Category.PERSONAL, personal.category)
        assertNull(personal.origin)
        assertNull(personal.destination)
        assertEquals(ClassifyReason.USER_EDIT, personal.reason)
    }

    @Test
    fun `업무끼리(일반 업무 ↔ 출퇴근) 바꾸면 장소를 그대로 둔다`() {
        val business = trip(category = Category.BUSINESS, origin = OFFICE, destination = CLIENT)

        val commute = business.reclassified(Category.COMMUTE, ClassifyReason.ADMIN_EDIT)

        assertEquals(Category.COMMUTE, commute.category)
        assertEquals(ClassifyReason.ADMIN_EDIT, commute.reason)
        assertEquals(OFFICE, commute.origin)
        assertEquals(CLIENT, commute.destination)
    }

    @Test
    fun `분류를 바꿔도 계기판·날짜·사람·비고는 그대로`() {
        val before = trip(startKm = 100.0, endKm = 110.0, personId = 2, note = "견적 협의", id = 7)

        val after = before.reclassified(Category.PERSONAL, ClassifyReason.USER_EDIT)

        assertEquals(
            before.copy(category = after.category, reason = after.reason, origin = null, destination = null),
            after,
        )
    }

    @Test
    fun `빠진 주행을 고르면 분류 대기가 풀리고 빠진 주행 표시는 남는다`() {
        val gap = trip(category = null, isGap = true, personId = null, origin = null, destination = null)

        val picked = gap.reclassified(Category.BUSINESS, ClassifyReason.USER_PICK)

        assertFalse(picked.isPending)
        assertTrue(picked.isGap)
        assertEquals(ClassifyReason.USER_PICK, picked.reason)
    }

    @Test
    fun `분류가 null 이면 분류 대기, 분류가 있으면 아님`() {
        assertTrue(trip(category = null, isGap = true).isPending)
        assertFalse(trip(category = Category.PERSONAL).isPending)
    }

    @Test
    fun `개인 운행만 isPersonal`() {
        assertTrue(trip(category = Category.PERSONAL).isPersonal)
        assertFalse(trip(category = Category.COMMUTE).isPersonal)
        assertFalse(trip(category = null, isGap = true).isPersonal)
    }

    @Test
    fun `사람을 안 고른 운행은 미지정, 빠진 주행은 사람을 묻지 않아 미지정이 아니다`() {
        assertTrue(trip(personId = null).isUnassigned)
        assertFalse(trip(personId = 1).isUnassigned)
        assertFalse(trip(personId = null, category = null, isGap = true).isUnassigned)
    }
}
