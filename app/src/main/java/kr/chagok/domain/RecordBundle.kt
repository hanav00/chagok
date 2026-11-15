package kr.chagok.domain

import kr.chagok.domain.model.BundleHeader
import kr.chagok.domain.model.ChagokSettings
import kr.chagok.domain.model.RecordUid
import java.security.MessageDigest
import java.time.LocalDate

/**
 * 내보내기·백업 묶음 규칙.
 * 묶음에는 차량번호가 아니라 차대번호 해시를 넣는다. 차량번호는 바뀔 수 있고, 차대번호 원문은 서버 색인 등에 남기지 않는다.
 * 해시는 가리기 용도다. 차대번호는 차 유리에도 적혀 있어 비밀이 아니므로, 숨겨야 하는 값은 묶음 본문 암호화로 지킨다.
 */
object RecordBundle {
    /** 차대번호 해시 (소문자 hex). 대소문자·앞뒤 공백은 무시한다. 비었으면 null */
    fun vinHash(vin: String): String? {
        val normalized = vin.trim().uppercase()
        if (normalized.isEmpty()) return null
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
            .digest((ChagokConfig.VIN_HASH_PREFIX + normalized).toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(ChagokConfig.VIN_HASH_HEX_LENGTH)
    }

    // TODO: 실제 내보내기·백업을 만들 때 묶음 머리로 쓴다
    fun header(
        settings: ChagokSettings,
        from: LocalDate,
        to: LocalDate,
        tripCount: Int,
        exportId: String = RecordUid.new(),
    ): BundleHeader = BundleHeader(
        formatVersion = ChagokConfig.BUNDLE_FORMAT_VERSION,
        vinHash = vinHash(settings.vehicle.vin),
        installUid = settings.installUid,
        exportId = exportId,
        from = from,
        to = to,
        tripCount = tripCount,
    )

    private const val HASH_ALGORITHM = "SHA-256"
}
