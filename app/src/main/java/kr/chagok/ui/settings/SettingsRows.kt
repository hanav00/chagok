package kr.chagok.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import kr.chagok.R
import kr.chagok.ui.components.HeaderButton
import kr.chagok.ui.components.ToggleSwitch
import kr.chagok.ui.components.pressable
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Spacing

/*
 * 설정 화면의 줄 모양.
 * 줄마다 위에 가는 선을 두고, 왼쪽 이름(흐린 글자)이 남는 폭을 다 쓴다.
 */

/** 섹션 안 두 번째 묶음 위에 더하는 여백 */
internal val SECOND_GROUP_EXTRA_GAP = Spacing.xs

private val ROW_VERTICAL_PADDING = Spacing.l
private val ROW_GAP = Spacing.s
private val LABEL_GAP = Spacing.xxs

/** 설정 한 줄의 틀: 위쪽 선 + 가로 배치. [onClick] 이 있으면 줄 전체가 눌린다 */
@Composable
internal fun SettingRow(onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Column {
        HorizontalDivider(color = ChagokColors.divider)
        Row(
            modifier = Modifier
                .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
                .padding(vertical = ROW_VERTICAL_PADDING),
            horizontalArrangement = Arrangement.spacedBy(ROW_GAP),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** 줄 왼쪽 이름과 그 아래 작은 설명 */
@Composable
internal fun RowScope.RowLabel(text: String, sub: String? = null) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LABEL_GAP)) {
        Text(text, color = ChagokColors.textSub)
        if (sub != null) Text(sub, color = ChagokColors.textDim)
    }
}

/** 이름과 값. 값이 비면 흐리게 "입력 필요", [onEdit] 이 있으면 줄을 눌러 고친다 */
@Composable
internal fun InfoRow(label: String, value: String, onEdit: (() -> Unit)? = null) {
    SettingRow(onClick = onEdit) {
        RowLabel(label)
        if (value.isNotBlank()) {
            Text(value, fontWeight = FontWeight.Bold)
        } else {
            Text(stringResource(R.string.form_missing), color = ChagokColors.textDim)
        }
        if (onEdit != null) EditChevron()
    }
}

/** 줄을 눌러 고칠 수 있다는 › 표시 */
@Composable
internal fun EditChevron() {
    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = ChagokColors.textDim)
}

/** 설명 한 줄 + 오른쪽 버튼 (버튼 이름이 곧 항목 이름이라 왼쪽에 다시 쓰지 않는다) */
@Composable
internal fun ActionRow(
    description: String,
    buttonText: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    SettingRow {
        RowLabel(description)
        HeaderButton(onClick = onClick, icon = icon, text = buttonText)
    }
}

/** 누르면 시트가 열리는 줄: 이름 + › (따로 버튼을 두지 않고 줄 전체를 누른다) */
@Composable
internal fun LinkRow(text: String, onClick: () -> Unit) {
    SettingRow(onClick = onClick) {
        Text(text, Modifier.weight(1f))
        EditChevron()
    }
}

@Composable
internal fun ToggleRow(label: String, isOn: Boolean, onToggle: () -> Unit) {
    SettingRow {
        RowLabel(label)
        ToggleSwitch(isOn = isOn, onToggle = onToggle, label = label)
    }
}
