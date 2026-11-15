package kr.chagok.ui.table

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import kr.chagok.R
import kr.chagok.ui.components.HeaderButton
import kr.chagok.ui.components.RoundIconButton
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes
import kr.chagok.ui.theme.TouchSizes

// 기록부 맨 위 줄: 달 넘기기와 내보내기. 분할 칸에는 서식 전체 보기 버튼이 더 붙는다.
// 넓은 창은 첫 화면(TableOverview)에서 들어왔으므로 돌아가는 버튼이 맨 앞에 선다
@Composable
internal fun TableHead(
    monthLabel: String,
    isLocked: Boolean,
    isWide: Boolean,
    isShowingAllColumns: Boolean,
    onShiftMonth: (Long) -> Unit,
    onExport: () -> Unit,
    onToggleColumns: () -> Unit,
    onBack: (() -> Unit)?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            HeaderButton(
                onClick = onBack,
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                text = stringResource(R.string.table_form_back),
            )
        }
        // 잠겨 있으면 오늘 운행만 보이므로 달을 넘기는 버튼을 두지 않는다
        if (!isLocked) MonthButton(Icons.Rounded.ChevronLeft, R.string.table_prev_month) { onShiftMonth(-1) }
        Text(monthLabel, fontSize = TextSizes.title, fontWeight = FontWeight.Bold)
        if (!isLocked) MonthButton(Icons.Rounded.ChevronRight, R.string.table_next_month) { onShiftMonth(1) }
        Spacer(Modifier.weight(1f))
        HeaderButton(
            onClick = onExport,
            icon = if (isLocked) Icons.Rounded.Lock else Icons.Rounded.QrCode2,
            text = stringResource(R.string.export_open),
            isAccent = true,
        )
        // 분할 칸에서는 아이콘만 (내보내기 버튼과 한 줄에 들어가게)
        if (!isWide) {
            HeaderButton(
                onClick = onToggleColumns,
                icon = if (isShowingAllColumns) Icons.Rounded.CloseFullscreen else Icons.Rounded.OpenInFull,
                contentDescription = stringResource(
                    if (isShowingAllColumns) R.string.table_narrow else R.string.table_wide,
                ),
            )
        }
    }
}

@Composable
private fun MonthButton(icon: ImageVector, @StringRes label: Int, onClick: () -> Unit) {
    RoundIconButton(
        icon = icon,
        contentDescription = stringResource(label),
        onClick = onClick,
        modifier = Modifier.size(TouchSizes.headButton),
        background = ChagokColors.card,
        tint = ChagokColors.text,
    )
}
