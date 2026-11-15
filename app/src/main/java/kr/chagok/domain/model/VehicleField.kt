package kr.chagok.domain.model

/**
 * 사람이 직접 넣는 서식 머리 칸. 순서는 서식 머리와 같다.
 * 차대번호는 차에서 읽어서, 사업연도는 규칙으로 정해서 여기 없다.
 */
enum class VehicleField {
    COMPANY,
    BIZ_NO,
    MODEL,
    PLATE,
}
