package kr.chagok.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

private val LABEL_GAP = Spacing.xxs

/**
 * 이름표 위 · 값 아래 한 칸 (기록부 사업연도 합계, 내보내기 요약).
 * 숫자는 흰색으로 두고, 핵심 칸([isKey], 업무사용비율)은 더 굵게.
 */
@Composable
fun FactCell(@StringRes label: Int, value: String, modifier: Modifier = Modifier, isKey: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(LABEL_GAP)) {
        Text(stringResource(label), color = ChagokColors.textSub, fontSize = TextSizes.caption)
        Text(
            text = value,
            color = ChagokColors.text,
            fontSize = TextSizes.subtitle,
            fontWeight = if (isKey) FontWeight.ExtraBold else FontWeight.Bold,
        )
    }
}
