package kr.chagok.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kr.chagok.R
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Spacing

private val EXAMPLE_ICON = 24.dp
private const val TICKER_MILLIS = 3_500L

/** Gleo 에게 하는 말 한 줄과 그 결과 */
private class GleoExample(@StringRes val phrase: Int, @StringRes val result: Int)

/**
 * Gleo 가 알아듣는 것을 확인한 문장만 둔다.
 * 앞에 "차곡에"가 없으면 Gleo 를 막 켠 첫 마디가 리마인더·잡담으로 빠져, 예시도 앱 이름을 넣은 그대로 보여 준다.
 */
private val COMMON_EXAMPLES = listOf(
    GleoExample(R.string.gleo_example_personal, R.string.gleo_example_personal_result),
    GleoExample(R.string.gleo_example_commute, R.string.gleo_example_commute_result),
    GleoExample(R.string.gleo_example_note, R.string.gleo_example_note_result),
)

private val SHARED_EXAMPLE = GleoExample(R.string.gleo_example_user, R.string.gleo_example_user_result)

/**
 * Gleo 로 말해서 고치기 예시 (설정 "Gleo로 말하기"). 공용차는 사람 정하기 예시를 더한다.
 * 문장은 따옴표로 굵게, 결과는 그 아래 흐리게.
 */
@Composable
fun GleoExampleList(isShared: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        examplesFor(isShared).forEach { ExampleRow(it) }
    }
}

/**
 * 같은 예시를 한 줄씩 차례로 넘겨 보여 준다. 목록을 다 펼치지 않고도 다 보여 주려고 높이를 한 줄로 묶는다
 * (첫 실행·홈 시작 가이드 "말해 보기"). 누르면 바로 다음 예시로 넘어간다.
 */
@Composable
fun GleoExampleTicker(isShared: Boolean, modifier: Modifier = Modifier) {
    val examples = examplesFor(isShared)
    var index by remember(examples.size) { mutableIntStateOf(0) }
    LaunchedEffect(index, examples.size) {
        delay(TICKER_MILLIS)
        index = (index + 1) % examples.size
    }
    Column(
        modifier.pressable { index = (index + 1) % examples.size },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            stringResource(R.string.try_more_count, index + 1, examples.size),
            color = ChagokColors.textSub,
        )
        AnimatedContent(
            targetState = index,
            transitionSpec = { (fadeIn() + slideInVertically { it / 3 }) togetherWith fadeOut() },
            label = "gleo-example",
        ) { shown ->
            ExampleRow(examples[shown])
        }
    }
}

private fun examplesFor(isShared: Boolean): List<GleoExample> =
    if (isShared) COMMON_EXAMPLES + SHARED_EXAMPLE else COMMON_EXAMPLES

@Composable
private fun ExampleRow(example: GleoExample) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Icon(
            Icons.Rounded.Mic,
            contentDescription = null,
            tint = ChagokColors.green,
            modifier = Modifier.size(EXAMPLE_ICON),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(stringResource(example.phrase), color = ChagokColors.text, fontWeight = FontWeight.Bold)
            Text(stringResource(example.result), color = ChagokColors.textSub)
        }
    }
}
