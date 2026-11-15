package kr.chagok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes
import kr.chagok.ui.theme.TouchSizes

private val INPUT_PADDING_H = Spacing.l
private val FOCUS_OUTLINE = 2.dp
private val LABEL_GAP = Spacing.xs

/**
 * 글자 입력 칸: 위에 이름, 아래 큰 칸. 정차 중 손가락·차 키보드로 넣기 좋게 버튼 높이와 같다.
 * [error] 가 있으면 칸 아래 주황 글자로 보인다.
 *
 * @param visualTransformation 저장 값과 보이는 값이 다를 때 (사업자등록번호 대시)
 */
@Composable
fun TextInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    val outline = when {
        error != null -> ChagokColors.pending
        isFocused -> ChagokColors.accent
        else -> null
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LABEL_GAP)) {
        Text(label, color = ChagokColors.textSub, fontWeight = FontWeight.SemiBold)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = TextSizes.subtitle, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(ChagokColors.accent),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            decorationBox = { field ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = TouchSizes.input)
                        .background(ChagokColors.card, Shapes.control)
                        .then(
                            if (outline != null) Modifier.border(FOCUS_OUTLINE, outline, Shapes.control) else Modifier,
                        )
                        .padding(horizontal = INPUT_PADDING_H),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, color = ChagokColors.textDim, fontSize = TextSizes.subtitle)
                    }
                    field()
                }
            },
        )
        if (error != null) Text(error, color = ChagokColors.pendingText)
    }
}
