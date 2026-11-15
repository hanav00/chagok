package kr.chagok.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 누가 쓰는 차인지 [VehicleUsage] */
class VehicleUsageTest {

    @Test
    fun `혼자 쓰는 차만 여러 명이 타는 차가 아니다 (공용차·기사 차는 '누가 타나요'를 묻는다)`() {
        assertFalse(VehicleUsage.SOLO.isShared)
        assertTrue(VehicleUsage.SHARED.isShared)
        assertTrue(VehicleUsage.CHAUFFEUR.isShared)
    }
}
