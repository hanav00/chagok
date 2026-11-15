package kr.chagok.domain

import kr.chagok.domain.model.ChagokSettings
import kr.chagok.domain.model.VehicleInfo
import kr.chagok.testing.TripFixtures.TODAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 내보내기·백업 묶음 머리: 어느 차·어느 설치에서 나온 기록인지 */
class RecordBundleTest {
    @Test
    fun `같은 차대번호는 대소문자·공백이 달라도 같은 해시다`() {
        assertEquals(RecordBundle.vinHash(VIN), RecordBundle.vinHash(" ${VIN.lowercase()} "))
    }

    @Test
    fun `다른 차대번호는 다른 해시이고 원문이 드러나지 않는다`() {
        val hash = requireNotNull(RecordBundle.vinHash(VIN))

        assertNotEquals(hash, RecordBundle.vinHash(OTHER_VIN))
        assertEquals(ChagokConfig.VIN_HASH_HEX_LENGTH, hash.length)
        assertFalse(hash.contains(VIN, ignoreCase = true))
    }

    @Test
    fun `차대번호를 못 읽었으면 해시는 없고 설치 식별값으로만 가린다`() {
        val header = RecordBundle.header(ChagokSettings(installUid = INSTALL_UID), TODAY, TODAY, tripCount = 0)

        assertNull(header.vinHash)
        assertEquals(INSTALL_UID, header.installUid)
    }

    @Test
    fun `묶음 머리에 형식 버전·차·설치·기간·건수가 들어간다`() {
        val settings = ChagokSettings(installUid = INSTALL_UID, vehicle = VehicleInfo(vin = VIN, plate = "12가 3456"))

        val header =
            RecordBundle.header(settings, TODAY.withDayOfMonth(1), TODAY, tripCount = 12, exportId = "export-1")

        assertEquals(ChagokConfig.BUNDLE_FORMAT_VERSION, header.formatVersion)
        assertEquals(RecordBundle.vinHash(VIN), header.vinHash)
        assertEquals(INSTALL_UID, header.installUid)
        assertEquals("export-1", header.exportId)
        assertEquals(TODAY.withDayOfMonth(1), header.from)
        assertEquals(TODAY, header.to)
        assertEquals(12, header.tripCount)
    }

    @Test
    fun `내보낼 때마다 묶음 식별값이 새로 생긴다`() {
        val settings = ChagokSettings(installUid = INSTALL_UID)

        assertNotEquals(
            RecordBundle.header(settings, TODAY, TODAY, 1).exportId,
            RecordBundle.header(settings, TODAY, TODAY, 1).exportId,
        )
    }

    private companion object {
        const val VIN = "KMHXX00XXXX000001"
        const val OTHER_VIN = "KMHXX00XXXX000002"
        const val INSTALL_UID = "3f1c2a9e-0000-4000-8000-000000000001"
    }
}
