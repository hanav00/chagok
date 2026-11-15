package kr.chagok.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kr.chagok.R
import kr.chagok.domain.ChagokConfig
import kr.chagok.ui.UserMessage
import kr.chagok.ui.theme.IconSizes
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.NoticeSizes
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TouchSizes

/**
 * 알림 줄에 지금 보이는 알림. [show] 가 몇 초 보이고 내린다 (Material SnackbarHostState 와 같은 일).
 * Pleos 는 일반 앱 알림·헤드업을 띄우지 않고 Toast 도 보장되지 않아서, 앱 화면 안에 직접 그린다.
 */
@Stable
class NoticeBarState {
    var current by mutableStateOf<UserMessage?>(null)
        private set

    private var dismissal: CompletableDeferred<Unit>? = null

    /** [message] 를 보이고 시간이 지나거나 [dismiss] 될 때까지 기다린다. 기다리는 쪽이 취소되면(출발) 바로 내린다 */
    suspend fun show(message: UserMessage) {
        val done = CompletableDeferred<Unit>()
        dismissal = done
        current = message
        try {
            withTimeoutOrNull(durationOf(message)) { done.await() }
        } finally {
            current = null
            dismissal = null
        }
    }

    fun dismiss() {
        dismissal?.complete(Unit)
    }

    private fun durationOf(message: UserMessage): Long =
        if (message.retry != null) ChagokConfig.NOTICE_WITH_ACTION_MILLIS else ChagokConfig.NOTICE_MILLIS
}

/**
 * 앱 창 아래 알림 한 줄: 경고 아이콘, 문구, [다시 하기] 버튼. 알림이 없으면 아무것도 그리지 않는다.
 * 시트 위에도 보이게 껍데기가 맨 위에 둔다. 다시 하기를 누르면 바로 내린다.
 */
@Composable
fun NoticeBar(state: NoticeBarState, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = state.current,
        modifier = modifier,
        transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith fadeOut() },
        contentAlignment = Alignment.BottomCenter,
        label = "notice",
    ) { message ->
        // 사라지는 동안에도 내리던 알림을 그대로 그린다 (AnimatedContent 가 앞 값을 들고 있다)
        if (message == null) {
            Box(Modifier)
        } else {
            NoticeLine(message, onRetry = {
                state.dismiss()
                message.retry?.invoke()
            })
        }
    }
}

@Composable
private fun NoticeLine(message: UserMessage, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(NoticeSizes.margin)
            .widthIn(max = NoticeSizes.maxWidth)
            .fillMaxWidth()
            .shadow(NoticeSizes.elevation, Shapes.card)
            .background(ChagokColors.notice, Shapes.card)
            // 화면 낭독기가 알림이 뜰 때 읽어 준다
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = Spacing.l, top = Spacing.s, end = Spacing.s, bottom = Spacing.s)
            // 버튼이 없어도 줄 높이가 같게 (다음 알림으로 바뀔 때 들썩이지 않게)
            .heightIn(min = TouchSizes.headButton),
        horizontalArrangement = Arrangement.spacedBy(TouchSizes.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (message.isDone) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
            contentDescription = null,
            tint = if (message.isDone) ChagokColors.accent else ChagokColors.pendingText,
            modifier = Modifier.size(IconSizes.small),
        )
        Text(
            text = stringResource(message.text, *message.args.toTypedArray()),
            color = ChagokColors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (message.retry != null) {
            HeaderButton(
                onClick = onRetry,
                icon = Icons.Rounded.Refresh,
                text = stringResource(R.string.notice_retry),
                isAccent = true,
            )
        }
    }
}
