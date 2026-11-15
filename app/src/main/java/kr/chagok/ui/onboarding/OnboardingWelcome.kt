package kr.chagok.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.chagok.R
import kr.chagok.domain.model.Category
import kr.chagok.ui.components.BlockDrop
import kr.chagok.ui.components.GravityEasing
import kr.chagok.ui.components.TripBlock
import kr.chagok.ui.components.dropping
import kr.chagok.ui.components.thudding
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

/** 아래부터 쌓이는 순서. 업무 차의 흔한 하루: 출근 · 거래처 · 거래처 · 개인 용무 */
private val HERO_BLOCKS = listOf(Category.COMMUTE, Category.BUSINESS, Category.BUSINESS, Category.PERSONAL)
private val HERO_BLOCK_WIDTH = 360.dp
private val HERO_BLOCK_HEIGHT = 72.dp
private val HERO_HEIGHT = 360.dp
private val HERO_PHONE_SIZE = 200.dp
private val BLOCK_GAP = Spacing.xs

/** 폰이 빠져나가는 거리와 블록이 떨어지기 시작하는 높이 */
private val PHONE_EXIT_DISTANCE = 380.dp
private const val PHONE_EXIT_DEGREES = -24f
private val BLOCK_DROP_DISTANCE = 520.dp
private val TAGLINE_RISE = 18.dp

private const val PHONE_IN_MILLIS = 420
private const val PHONE_HOLD_MILLIS = 500L
private const val PHONE_EXIT_MILLIS = 520
private const val BLOCK_STAGGER_MILLIS = 330L
private const val TAGLINE_DELAY_MILLIS = 500L
private const val TAGLINE_STAGGER_MILLIS = 260L
private const val TAGLINE_MILLIS = 600
private const val TAGLINE_LINES = 2

/** 다 쌓인 뒤 잠깐 보여 주고, 블록을 지운 다음 처음부터 다시 */
private const val LOOP_HOLD_MILLIS = 2400L
private const val STACK_FADE_MILLIS = 400
private const val LOOP_PAUSE_MILLIS = 300L

/**
 * 시작: 폰이 크게 떴다가 옆으로 빠지고, 운행 블록이 하나씩 떨어져 쌓인 뒤 표어가 뜬다.
 * 글 대신 움직임이 "폰 없이, 차가 쌓는다"를 말한다.
 * 표어는 한 번 뜨면 그대로 두고, 폰과 블록 장면만 다음 단계로 넘어갈 때까지 되풀이한다.
 */
@Composable
fun WelcomeStep(isWide: Boolean) {
    val phoneIn = remember { Animatable(0f) }
    val phoneOut = remember { Animatable(0f) }
    val drops = remember { HERO_BLOCKS.map { BlockDrop(isLanded = false) } }
    val stack = remember { Animatable(1f) }
    val lines = remember { List(TAGLINE_LINES) { Animatable(0f) } }
    LaunchedEffect(Unit) {
        var isFirstRound = true
        while (true) {
            phoneIn.animateTo(1f, tween(PHONE_IN_MILLIS))
            delay(PHONE_HOLD_MILLIS)
            phoneOut.animateTo(1f, tween(PHONE_EXIT_MILLIS, easing = GravityEasing))
            drops.forEach { drop ->
                launch { drop.play() }
                delay(BLOCK_STAGGER_MILLIS)
            }
            delay(TAGLINE_DELAY_MILLIS)
            if (isFirstRound) {
                lines.forEach { line ->
                    launch { line.animateTo(1f, tween(TAGLINE_MILLIS)) }
                    delay(TAGLINE_STAGGER_MILLIS)
                }
                isFirstRound = false
            }
            delay(LOOP_HOLD_MILLIS)
            stack.animateTo(0f, tween(STACK_FADE_MILLIS))
            drops.forEach { it.reset() }
            phoneIn.snapTo(0f)
            phoneOut.snapTo(0f)
            stack.snapTo(1f)
            delay(LOOP_PAUSE_MILLIS)
        }
    }
    Column(
        Modifier.fillMaxWidth().padding(top = Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxl),
    ) {
        Box(Modifier.height(HERO_HEIGHT).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            Icon(
                Icons.Rounded.Smartphone,
                contentDescription = null,
                tint = ChagokColors.textSub,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(HERO_PHONE_SIZE)
                    .graphicsLayer {
                        val scale = 0.6f + 0.4f * phoneIn.value
                        scaleX = scale
                        scaleY = scale
                        alpha = phoneIn.value * (1f - phoneOut.value)
                        translationX = -PHONE_EXIT_DISTANCE.toPx() * phoneOut.value
                        rotationZ = PHONE_EXIT_DEGREES * phoneOut.value
                    },
            )
            Column(
                modifier = Modifier.graphicsLayer { alpha = stack.value },
                verticalArrangement = Arrangement.spacedBy(BLOCK_GAP),
            ) {
                // 위에서부터 그리므로 마지막에 쌓이는 블록이 맨 위다. 아래 블록은 위에 떨어지는 블록마다 눌린다
                HERO_BLOCKS.indices.reversed().forEach { index ->
                    val thuds = drops.drop(index + 1)
                        .fold<BlockDrop, Modifier>(Modifier) { acc, above -> acc.thudding(above) }
                    TripBlock(
                        category = HERO_BLOCKS[index],
                        isGap = false,
                        shape = Shapes.blockLarge,
                        modifier = Modifier
                            .size(HERO_BLOCK_WIDTH, HERO_BLOCK_HEIGHT)
                            .then(thuds)
                            .dropping(drops[index], BLOCK_DROP_DISTANCE),
                    )
                }
            }
        }
        Tagline(rememberTagline(), lines, if (isWide) TextSizes.hero else TextSizes.headline)
    }
}

@Composable
private fun Tagline(lines: List<AnnotatedString>, progress: List<Animatable<Float, *>>, fontSize: TextUnit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        lines.forEachIndexed { index, line ->
            Text(
                text = line,
                modifier = Modifier.graphicsLayer {
                    val shown = progress[index].value
                    alpha = shown
                    translationY = TAGLINE_RISE.toPx() * (1f - shown)
                },
                fontSize = fontSize,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// 표어 두 줄. 앱 이름은 강조색으로
@Composable
private fun rememberTagline(): List<AnnotatedString> {
    val text = stringResource(R.string.onboarding_welcome_title)
    val appName = stringResource(R.string.app_name)
    return remember(text, appName) {
        text.split('\n').map { line ->
            buildAnnotatedString {
                append(line)
                val at = line.indexOf(appName)
                if (at >= 0) addStyle(SpanStyle(color = ChagokColors.accent), at, at + appName.length)
            }
        }
    }
}
