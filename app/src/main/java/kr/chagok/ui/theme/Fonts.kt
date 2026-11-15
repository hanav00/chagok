package kr.chagok.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import kr.chagok.R

/** 앱에서 쓰는 굵기. Asta Sans 가변 글꼴은 300~800 을 지원한다 */
private val USED_WEIGHTS = listOf(
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
    FontWeight.ExtraBold,
)

/**
 * Pleos 시스템 글꼴 Asta Sans (OFL, 라이선스는 assets/licenses/asta_sans_OFL.txt).
 * 가변 글꼴 한 파일을 굵기마다 wght 축 값으로 나눠 등록한다. variationSettings 가 실험 API 라 OptIn 한다.
 */
@OptIn(ExperimentalTextApi::class)
val AppFontFamily = FontFamily(
    USED_WEIGHTS.map { weight ->
        Font(R.font.asta_sans, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    },
)
