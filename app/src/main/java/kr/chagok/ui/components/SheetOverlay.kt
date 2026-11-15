package kr.chagok.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing

private const val SHEET_MAX_HEIGHT_RATIO = 0.86f
private val SHEET_MAX_WIDTH = 760.dp

/**
 * 앱 창 아래에서 올라와 창을 덮는 시트. 다른 앱 위로 띄우지 않으려고 대화상자 창 대신 앱 화면 안에 그린다.
 *
 * @param isOutsideDismissible false 면 바깥·뒤로 가기로는 닫지 않고 닫기 버튼으로만 닫는다
 *   (복구키처럼 실수로 놓치면 안 되는 단계)
 */
@Composable
fun SheetOverlay(
    onDismiss: () -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    isOutsideDismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 바깥으로 못 닫는 단계에서도 뒤로 가기가 아래 화면까지 내려가지 않게 받아서 버린다
    BackHandler(onBack = { if (isOutsideDismissible) onDismiss() })
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ChagokColors.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (isOutsideDismissible) onDismiss() },
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = SHEET_MAX_WIDTH)
                .fillMaxWidth()
                .heightIn(max = maxHeight * SHEET_MAX_HEIGHT_RATIO)
                .background(ChagokColors.window, Shapes.sheet)
                // 시트 안을 눌러도 닫히지 않게 바깥 클릭을 막는다
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.xxl, top = Spacing.xl, end = Spacing.xxl, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
            horizontalAlignment = horizontalAlignment,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { header() }
                RoundIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.close),
                    onClick = onDismiss,
                    background = ChagokColors.card,
                    tint = ChagokColors.text,
                )
            }
            content()
        }
    }
}
