package kr.chagok.ui.vehicle

import androidx.annotation.StringRes
import kr.chagok.R
import kr.chagok.domain.model.VehicleField

/** 서식 머리 칸 이름 (설정 줄 이름과 같다). 차량·사업자 시트와 내보내기 "빠진 값" 안내가 같이 쓴다 */
@get:StringRes
val VehicleField.labelRes: Int
    get() = when (this) {
        VehicleField.COMPANY -> R.string.settings_company
        VehicleField.BIZ_NO -> R.string.settings_biz_no
        VehicleField.MODEL -> R.string.settings_model
        VehicleField.PLATE -> R.string.settings_plate
    }
