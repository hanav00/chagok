package kr.chagok.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.chagok.R
import kr.chagok.domain.ChagokConfig
import kr.chagok.ui.appContainer
import kr.chagok.ui.components.ActionButton
import kr.chagok.ui.components.ButtonStyle
import kr.chagok.ui.components.Hint
import kr.chagok.ui.components.SheetOverlay
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

private val SUB_MAX_WIDTH = 520.dp
private const val SUB_LINE_HEIGHT = 1.45f
private val KEY_LETTER_SPACING = 0.08.em

/**
 * 관리자 PIN 시트. 숫자판 하나로 PIN 넣기·정하기·바꾸기·넘기기·복구키로 다시 정하기를 하고, 키보드는 쓰지 않는다.
 * 단계 흐름은 [PinFlow] 가 맡는다.
 */
@Composable
fun PinSheet(mode: PinMode, onDismiss: () -> Unit, onDone: () -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val currentOnDone by rememberUpdatedState(onDone)
    val flow = remember(mode) {
        PinFlow(mode, container.admin, container.messages, scope, onFinish = { currentOnDone() })
    }
    val admin by container.admin.state.collectAsStateWithLifecycle()

    SheetOverlay(
        onDismiss = onDismiss,
        header = { PinHeader() },
        horizontalAlignment = Alignment.CenterHorizontally,
        // 새 복구키를 보는 동안은 바깥을 스쳐도 닫히지 않는다 (닫기 버튼은 "저장하지 않고 취소")
        isOutsideDismissible = flow.stage != PinStage.KEY,
    ) {
        if (flow.stage == PinStage.KEY) {
            NewKeyView(flow.newKey, onSaved = flow::confirmKeySaved)
        } else {
            // 너무 많이 틀리면 PIN 으로는 열지 않는다. 복구키로만
            val isBlocked = flow.stage == PinStage.PIN && admin.isPinBlocked
            EntryView(flow, isBlocked, triesLeft = admin.triesLeft)
        }
    }
}

@Composable
private fun PinHeader() {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null)
        Text(stringResource(R.string.admin_title), fontSize = TextSizes.title, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ColumnScope.EntryView(flow: PinFlow, isBlocked: Boolean, triesLeft: Int) {
    PinTitle(stageTitle(flow.stage))
    stageSub(flow.stage, flow.mode)?.let { PinSub(it) }
    if (flow.stage == PinStage.RECOVER) RecoveryKeyInput(flow.input) else PinDots(flow.input.length)
    Text(
        text = messageText(flow.message, isBlocked, triesLeft),
        color = ChagokColors.redText,
        textAlign = TextAlign.Center,
    )
    PinPad(isBlocked = isBlocked, onDigit = flow::press, onDelete = flow::delete)
    when (flow.stage) {
        PinStage.PIN -> ActionButton(
            text = stringResource(R.string.admin_forgot),
            onClick = flow::startRecovery,
            // 막혔으면 이것만 남는 길이라 강조한다
            style = if (isBlocked) ButtonStyle.Primary else ButtonStyle.Secondary,
        )
        PinStage.RECOVER -> Row(
            modifier = Modifier.widthIn(max = SUB_MAX_WIDTH).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            ActionButton(
                text = stringResource(R.string.admin_back_to_pin),
                onClick = flow::backToPin,
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                text = stringResource(R.string.admin_recover_submit),
                onClick = flow::submitRecovery,
                modifier = Modifier.weight(1f),
                style = ButtonStyle.Primary,
                enabled = flow.isRecoveryFilled,
            )
        }
        else -> Unit
    }
}

/** PIN 을 정한 뒤 새 복구키를 한 번만 보여 준다 */
@Composable
private fun ColumnScope.NewKeyView(key: String, onSaved: () -> Unit) {
    PinTitle(stringResource(R.string.admin_key_title))
    PinSub(stringResource(R.string.admin_key_sub))
    Text(
        text = groupRecoveryKey(key),
        modifier = Modifier
            .padding(vertical = Spacing.xs)
            .background(ChagokColors.section, Shapes.control)
            .padding(horizontal = Spacing.xxl, vertical = Spacing.l),
        fontSize = TextSizes.headline,
        fontWeight = FontWeight.ExtraBold,
        // 옮겨 적는 숫자라 자리마다 폭이 같게
        style = LocalTextStyle.current.copy(letterSpacing = KEY_LETTER_SPACING, fontFeatureSettings = "tnum"),
    )
    Hint(text = stringResource(R.string.admin_key_warn), isWarning = true)
    ActionButton(
        text = stringResource(R.string.admin_key_done),
        onClick = onSaved,
        modifier = Modifier.fillMaxWidth(),
        icon = Icons.Rounded.Check,
        style = ButtonStyle.Primary,
    )
}

@Composable
private fun PinTitle(text: String) {
    Text(text, fontSize = TextSizes.title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
}

@Composable
private fun PinSub(text: String) {
    Text(
        text = text,
        modifier = Modifier.widthIn(max = SUB_MAX_WIDTH),
        color = ChagokColors.textSub,
        textAlign = TextAlign.Center,
        style = LocalTextStyle.current.copy(lineHeight = TextSizes.body * SUB_LINE_HEIGHT),
    )
}

@Composable
private fun stageTitle(stage: PinStage): String = when (stage) {
    PinStage.PIN -> stringResource(R.string.admin_unlock_title)
    PinStage.RECOVER -> stringResource(R.string.admin_recover_title, ChagokConfig.RECOVERY_KEY_LENGTH)
    PinStage.CONFIRM -> stringResource(R.string.admin_confirm_title)
    PinStage.NEW, PinStage.KEY -> stringResource(R.string.admin_new_title, ChagokConfig.PIN_LENGTH)
}

/** 단계 설명. 새 PIN 은 여는 이유마다 다르고, 한 번 더 누를 때는 없다 */
@Composable
private fun stageSub(stage: PinStage, mode: PinMode): String? = when (stage) {
    PinStage.PIN -> stringResource(R.string.admin_unlock_sub)
    PinStage.RECOVER -> stringResource(R.string.admin_recover_sub)
    PinStage.CONFIRM, PinStage.KEY -> null
    PinStage.NEW -> when (mode) {
        PinMode.SETUP -> stringResource(R.string.admin_setup_sub)
        PinMode.HANDOVER -> stringResource(R.string.admin_handover_sub)
        // 복구키로 다시 정할 때(UNLOCK)도 복구키는 그대로다
        PinMode.CHANGE, PinMode.UNLOCK -> stringResource(R.string.admin_change_sub)
    }
}

/** 빈 글자여도 한 줄 높이를 차지해 숫자판이 위아래로 흔들리지 않는다 */
@Composable
private fun messageText(message: PinMessage?, isBlocked: Boolean, triesLeft: Int): String = when {
    isBlocked -> stringResource(R.string.admin_blocked, ChagokConfig.PIN_MAX_TRIES)
    message == PinMessage.WRONG -> stringResource(R.string.admin_wrong, triesLeft)
    message == PinMessage.RECOVER_WRONG -> stringResource(R.string.admin_recover_wrong)
    message == PinMessage.MISMATCH -> stringResource(R.string.admin_mismatch)
    else -> ""
}
