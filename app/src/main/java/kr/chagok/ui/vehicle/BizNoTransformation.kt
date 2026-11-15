package kr.chagok.ui.vehicle

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import kr.chagok.domain.ChagokConfig
import kr.chagok.domain.VehicleRules

/**
 * 사업자등록번호 칸: 숫자만 들고 있고 보일 때만 "123-45-67890" 으로 대시를 끼운다.
 * 커서 위치도 대시 수만큼 옮겨 준다 (대시 앞에 커서를 둔다).
 */
internal object BizNoTransformation : VisualTransformation {
    // 대시가 들어가는 자리 (숫자 기준 몇 자리 뒤)
    private val dashAfter = ChagokConfig.BIZ_NO_DASH_AFTER.sorted()

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val shown = VehicleRules.formatBizNo(digits)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                (offset + dashAfter.count { it < offset }).coerceAtMost(shown.length)

            override fun transformedToOriginal(offset: Int): Int =
                (offset - dashAfter.withIndex().count { (index, after) -> after + index < offset })
                    .coerceIn(0, digits.length)
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}
