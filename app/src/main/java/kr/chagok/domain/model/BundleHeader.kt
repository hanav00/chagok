package kr.chagok.domain.model

import java.time.LocalDate

/**
 * 내보내기·백업 묶음의 머리. 여러 차의 묶음을 한곳에 모아 합칠 때 어느 차 기록인지 가린다.
 * 암호화할 때는 이 머리를 추가 인증 데이터로 묶는다.
 * 만드는 곳은 [kr.chagok.domain.RecordBundle.header].
 *
 * @property vinHash 차대번호 해시. 같은 차면 앱을 다시 깔아도 같다. 차대번호를 못 읽었으면 null
 * @property installUid 이 앱 설치의 식별값. 차대번호를 늦게 읽어도 같은 설치에서 나온 묶음끼리는 이어진다
 * @property exportId 이 묶음 하나의 식별값. QR 여러 장 중 다른 묶음의 장이 섞이면 버린다
 * @property from 묶음에 든 운행의 첫날 (포함)
 * @property to 묶음에 든 운행의 마지막 날 (포함)
 */
data class BundleHeader(
    val formatVersion: Int,
    val vinHash: String?,
    val installUid: String,
    val exportId: String,
    val from: LocalDate,
    val to: LocalDate,
    val tripCount: Int,
)
