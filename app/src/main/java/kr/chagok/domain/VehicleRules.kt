package kr.chagok.domain

import kr.chagok.domain.model.VehicleField
import kr.chagok.domain.model.VehicleInfo

/**
 * 서식 머리 값(법인명·사업자등록번호·차종·번호판) 규칙.
 * 사업자등록번호는 숫자 10자리를 "123-45-67890" 으로 저장한다 (국세청 표기).
 */
object VehicleRules {
    /** 아직 안 넣은 칸 (서식 머리 순서). 내보내기 화면이 "빠진 값 채우기"로 이 칸만 묻는다 */
    fun missingFields(info: VehicleInfo): List<VehicleField> =
        VehicleField.entries.filter { valueOf(info, it).isBlank() }

    fun valueOf(info: VehicleInfo, field: VehicleField): String = when (field) {
        VehicleField.COMPANY -> info.company
        VehicleField.BIZ_NO -> info.bizNo
        VehicleField.MODEL -> info.model
        VehicleField.PLATE -> info.plate
    }

    fun withValue(info: VehicleInfo, field: VehicleField, value: String): VehicleInfo = when (field) {
        VehicleField.COMPANY -> info.copy(company = value)
        VehicleField.BIZ_NO -> info.copy(bizNo = value)
        VehicleField.MODEL -> info.copy(model = value)
        VehicleField.PLATE -> info.copy(plate = value)
    }

    /** 입력 중인 글자를 칸에 맞게 거른다: 사업자등록번호는 숫자만, 나머지는 길이만 자른다 */
    fun cleanInput(field: VehicleField, text: String): String = when (field) {
        VehicleField.BIZ_NO -> bizNoDigits(text)
        else -> text.take(ChagokConfig.VEHICLE_TEXT_MAX)
    }

    /** 사업자등록번호에서 숫자만 (저장된 "123-45-67890" 을 다시 고칠 때도) */
    fun bizNoDigits(text: String): String = text.filter(Char::isDigit).take(ChagokConfig.BIZ_NO_LENGTH)

    /** 비었거나 10자리를 다 넣었으면 저장할 수 있다. 몇 자리만 넣은 것은 서식에 틀린 번호가 들어가니 막는다 */
    fun isBizNoValid(digits: String): Boolean = digits.isEmpty() || digits.length == ChagokConfig.BIZ_NO_LENGTH

    /** 숫자 10자리 → "123-45-67890". 다 안 찼으면 있는 만큼만 끊어 쓴다 (입력 중 보여 주기) */
    fun formatBizNo(digits: String): String = buildString {
        digits.forEachIndexed { index, char ->
            if (index in ChagokConfig.BIZ_NO_DASH_AFTER) append(BIZ_NO_DASH)
            append(char)
        }
    }

    /**
     * 저장할 값으로 다듬는다: 앞뒤 공백·겹친 공백을 지우고 사업자등록번호는 대시를 넣는다.
     * 사업자등록번호는 입력 중 숫자만 들고 있다 ([cleanInput]).
     */
    fun normalized(info: VehicleInfo): VehicleInfo = VehicleInfo(
        vin = info.vin,
        model = tidy(info.model),
        plate = tidy(info.plate),
        company = tidy(info.company),
        bizNo = formatBizNo(bizNoDigits(info.bizNo)),
    )

    private fun tidy(text: String): String = text.trim().replace(SPACES, " ")

    private const val BIZ_NO_DASH = '-'
    private val SPACES = Regex("\\s+")
}
