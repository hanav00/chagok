package kr.chagok.domain

import kr.chagok.domain.model.VehicleField
import kr.chagok.domain.model.VehicleInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleRulesTest {

    @Test
    fun `빠진 칸은 서식 머리 순서로, 공백만 있어도 빠진 것`() {
        val info = VehicleInfo(vin = "KMH123", model = "그랜저", plate = "  ", company = "", bizNo = "123-45-67890")
        assertEquals(listOf(VehicleField.COMPANY, VehicleField.PLATE), VehicleRules.missingFields(info))
    }

    @Test
    fun `다 넣으면 빠진 칸이 없다 (차대번호는 묻지 않는다)`() {
        val info = VehicleInfo(model = "그랜저", plate = "123가 4567", company = "(주)성일정밀", bizNo = "123-45-67890")
        assertTrue(VehicleRules.missingFields(info).isEmpty())
    }

    @Test
    fun `사업자등록번호 입력은 숫자만 10자리까지`() {
        assertEquals("1234567890", VehicleRules.cleanInput(VehicleField.BIZ_NO, "123-45-67890 99"))
        assertEquals("", VehicleRules.cleanInput(VehicleField.BIZ_NO, "가나다"))
    }

    @Test
    fun `다른 칸은 글자 수만 자른다`() {
        val long = "가".repeat(ChagokConfig.VEHICLE_TEXT_MAX + 5)
        assertEquals(ChagokConfig.VEHICLE_TEXT_MAX, VehicleRules.cleanInput(VehicleField.COMPANY, long).length)
        assertEquals("123가 4567", VehicleRules.cleanInput(VehicleField.PLATE, "123가 4567"))
    }

    @Test
    fun `사업자등록번호는 비었거나 10자리일 때만 저장할 수 있다`() {
        assertTrue(VehicleRules.isBizNoValid(""))
        assertTrue(VehicleRules.isBizNoValid("1234567890"))
        assertFalse(VehicleRules.isBizNoValid("12345"))
    }

    @Test
    fun `사업자등록번호는 3-2-5 로 끊는다, 입력 중이면 있는 만큼`() {
        assertEquals("123-45-67890", VehicleRules.formatBizNo("1234567890"))
        assertEquals("123-4", VehicleRules.formatBizNo("1234"))
        assertEquals("123", VehicleRules.formatBizNo("123"))
        assertEquals("", VehicleRules.formatBizNo(""))
    }

    @Test
    fun `저장 전 다듬기 - 공백 정리, 사업자등록번호 대시, 차대번호는 그대로`() {
        val draft = VehicleInfo(
            vin = "KMH123",
            model = " 그랜저 ",
            plate = "123가   4567",
            company = "(주)성일정밀 ",
            bizNo = "1234567890",
        )
        val expected = VehicleInfo(
            vin = "KMH123",
            model = "그랜저",
            plate = "123가 4567",
            company = "(주)성일정밀",
            bizNo = "123-45-67890",
        )
        assertEquals(expected, VehicleRules.normalized(draft))
    }

    @Test
    fun `칸 하나만 바꾸고 읽기`() {
        val info = VehicleRules.withValue(VehicleInfo(), VehicleField.PLATE, "12가 3456")
        assertEquals("12가 3456", VehicleRules.valueOf(info, VehicleField.PLATE))
        assertEquals("", info.company)
    }
}
