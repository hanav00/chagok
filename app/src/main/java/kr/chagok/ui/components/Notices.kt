package kr.chagok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.ui.theme.IconSizes
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

private val LINE_ICON = 26.dp
private val MUTED_ICON = 24.dp
private const val WARNING_TINT_ALPHA = 0.12f
private const val LOOSE_LINE_HEIGHT = 1.5f

/**
 * 안내 한 줄: 사용법 안내는 처음 며칠만, 되돌릴 수 없는 일은 [isWarning].
 * 카드 안에서는 [isBoxed] 를 끄고 글자만 둔다 (카드 안에 상자를 또 두지 않는다).
 */
@Composable
fun Hint(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Info,
    isWarning: Boolean = false,
    isBoxed: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isBoxed) {
                    Modifier
                        .background(ChagokColors.section, Shapes.control)
                        .padding(horizontal = Spacing.l, vertical = Spacing.m)
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(
            imageVector = if (isWarning) Icons.Rounded.Warning else icon,
            contentDescription = null,
            tint = if (isWarning) ChagokColors.pending else ChagokColors.accent,
            modifier = Modifier.size(LINE_ICON),
        )
        Text(
            text,
            color = ChagokColors.textSub,
            style = LocalTextStyle.current.copy(lineHeight = TextSizes.body * LOOSE_LINE_HEIGHT),
        )
    }
}

/** 경고 한 줄: 기록 연속성이 끊겼을 때, 내보내기 분류 대기 */
@Composable
fun WarningLine(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ChagokColors.pending.copy(alpha = WARNING_TINT_ALPHA), Shapes.control)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Error,
            contentDescription = null,
            tint = ChagokColors.pendingText,
            modifier = Modifier.size(IconSizes.small),
        )
        Text(text, color = ChagokColors.pendingText)
    }
}

/** 흐린 한 줄. 앞에 아이콘을 붙일 수 있다 */
@Composable
fun MutedText(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = ChagokColors.textSub,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(MUTED_ICON))
        Text(text, color = color)
    }
}

/** 출처·각주: 가장 작은 흐린 글자 */
@Composable
fun SourceText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = ChagokColors.textDim,
        fontSize = TextSizes.caption,
        style = LocalTextStyle.current.copy(lineHeight = TextSizes.caption * LOOSE_LINE_HEIGHT),
    )
}

/** 관리자만 쓸 수 있는 곳의 잠김 한 줄 + PIN 버튼: 기록부·설정 */
@Composable
fun LockedLine(text: String, onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ChagokColors.section, Shapes.control)
            .padding(start = Spacing.l, top = Spacing.s, end = Spacing.s, bottom = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            tint = ChagokColors.textSub,
            modifier = Modifier.size(LINE_ICON),
        )
        Text(text, color = ChagokColors.textSub, modifier = Modifier.weight(1f))
        HeaderButton(
            onClick = onUnlock,
            icon = Icons.Rounded.AdminPanelSettings,
            text = stringResource(R.string.admin_unlock_button),
        )
    }
}
