package kr.chagok.ui.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.ui.components.HeaderButton
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

private val HEADER_HEIGHT = 108.dp
private val APP_TILE_SIZE = 56.dp
private val APP_TILE_GLYPH = 36.dp
private val RECORDING_DOT = 10.dp
private const val RECORDING_TINT_ALPHA = 0.16f

/**
 * 앱 공통 헤더: 앱 이름, 주행 중이면 "기록 중", 관리자 모드면 "관리자 잠그기" 버튼.
 * 넓은 창이면 [tabs] 를 앱 이름 옆에 둔다.
 * 관리자 모드 표시를 따로 두지 않는다. 버튼이 보이면 열려 있다는 뜻이다.
 * 앱이 창 크기를 바꾸지 않으므로 넓게·분할 전환 버튼은 없다.
 */
@Composable
fun AppHeader(
    isDriving: Boolean,
    isAdminUnlocked: Boolean,
    onLockAdmin: () -> Unit,
    tabs: (@Composable () -> Unit)? = null,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HEADER_HEIGHT)
                // 넓은 창(헤더 안 탭)은 화면 여백과 같은 폭으로 띄운다
                .padding(horizontal = if (tabs != null) Spacing.wideHorizontal else Spacing.xxl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTile()
            Text(stringResource(R.string.app_name), fontSize = TextSizes.title, fontWeight = FontWeight.SemiBold)
            tabs?.invoke()
            if (isDriving) RecordingPill()
            if (isAdminUnlocked && !isDriving) {
                HeaderButton(
                    onClick = onLockAdmin,
                    icon = Icons.Rounded.Lock,
                    text = stringResource(R.string.header_admin_lock),
                )
            }
        }
        HorizontalDivider(color = ChagokColors.divider)
    }
}

// 앱 아이콘과 같은 블록 탑. 바탕이 창과 같은 색이라 블록만 보인다
@Composable
private fun AppTile() {
    Box(Modifier.size(APP_TILE_SIZE), contentAlignment = Alignment.Center) {
        Image(
            painterResource(R.drawable.ic_app_blocks),
            contentDescription = null,
            modifier = Modifier.size(APP_TILE_GLYPH),
        )
    }
}

// 주행 중에만 보인다. 시선을 끌지 않게 깜빡이지 않는다
@Composable
private fun RecordingPill() {
    Row(
        modifier = Modifier
            .background(ChagokColors.red.copy(alpha = RECORDING_TINT_ALPHA), Shapes.pill)
            .padding(horizontal = Spacing.m, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(RECORDING_DOT).background(ChagokColors.red, Shapes.pill))
        Text(stringResource(R.string.header_recording), color = ChagokColors.redText, fontWeight = FontWeight.Bold)
    }
}

