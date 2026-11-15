package kr.chagok.ui.guide

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kr.chagok.R
import kr.chagok.domain.model.Category
import kr.chagok.ui.components.color
import kr.chagok.ui.components.drivingLabelRes
import kr.chagok.ui.components.dropping
import kr.chagok.ui.components.flippingX
import kr.chagok.ui.components.pressable
import kr.chagok.ui.components.rememberBlockDrop
import kr.chagok.ui.components.rememberFlip
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.IconSizes
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

private val GEARS = listOf("P", "R", "N", "D")
private val DRIVE_BAR_WIDTH = 6.dp
private val CARD_DROP_DISTANCE = 80.dp
private val APPEAR_RISE = 16.dp
private const val APPEAR_MILLIS = 300
private const val SPEECH_DELAY_MILLIS = 700L
private const val PARKED_SPEECH_DELAY_MILLIS = 900L
private const val TYPE_MILLIS = 1_400
private const val FLIP_DELAY_MILLIS = 500L
private const val GLEO_TINT = 0.12f

/** 무대에 그릴 한 장면. [take] 는 처음부터 보기·장면 고르기마다 늘어 같은 장면도 처음부터 다시 그린다 */
internal data class HowShot(val scene: HowScene, val take: Int)

/**
 * 견본 운행 하나를 그리는 무대. 위 줄의 기어와 거리는 장면을 넘어 이어서 움직이고, 가운데만 장면마다 바뀐다.
 */
@Composable
internal fun HowStage(shot: HowShot, story: HowStory, onSpeak: (String) -> Unit, modifier: Modifier) {
    Column(
        modifier.background(ChagokColors.card, Shapes.card).padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GearRow(isDriving = shot.scene == HowScene.DRIVING || shot.scene == HowScene.CHANGE)
            TripDistance(shot)
        }
        AnimatedContent(
            targetState = shot,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = { (fadeIn() + slideInHorizontally { it / 8 }) togetherWith fadeOut() },
            label = "how-scene",
        ) { current ->
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
            ) {
                when (current.scene) {
                    HowScene.IGNITION -> IgnitionScene(story.ignition, onSpeak)
                    HowScene.DRIVING -> DriveLine(story.defaultCategory)
                    HowScene.CHANGE -> ChangeScene(from = story.defaultCategory, to = story.endCategory)
                    HowScene.PARKED -> ParkedScene(story, onSpeak)
                }
            }
        }
    }
}

@Composable
private fun IgnitionScene(sentence: String, onSpeak: (String) -> Unit) {
    Text(stringResource(R.string.onboarding_how_recording), fontSize = TextSizes.title, fontWeight = FontWeight.Bold)
    Appear(SPEECH_DELAY_MILLIS) { Speech(sentence, onListen = { onSpeak(sentence) }) }
}

// Gleo 에게 하는 말이 한 글자씩 찍히고, 다 말하면 주행 줄이 뒤집혀 바뀐다
@Composable
private fun ChangeScene(from: Category, to: Category) {
    var shown by remember { mutableStateOf(from) }
    val flip = rememberFlip(shown)
    val typed = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        typed.animateTo(1f, tween(TYPE_MILLIS, easing = LinearEasing))
        delay(FLIP_DELAY_MILLIS)
        shown = to
    }
    DriveLine(flip.shown, Modifier.flippingX(flip))
    GleoAsk(stringResource(to.phraseRes), typed.value)
}

// "방금 운행" 카드가 위에서 떨어지고, 이어서 도착 안내
@Composable
private fun ParkedScene(story: HowStory, onSpeak: (String) -> Unit) {
    val drop = rememberBlockDrop(Unit)
    LaunchedEffect(drop) { drop.play() }
    ArrivalCard(story.endCategory, story.place, Modifier.dropping(drop, CARD_DROP_DISTANCE))
    Appear(PARKED_SPEECH_DELAY_MILLIS) { Speech(story.arrival, onListen = { onSpeak(story.arrival) }) }
}

/** 처음엔 숨겨 두었다가 [delayMillis] 뒤에 아래에서 떠오른다. 자리는 처음부터 차지해 무대가 흔들리지 않는다 */
@Composable
private fun Appear(delayMillis: Long, content: @Composable () -> Unit) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        shown.animateTo(1f, tween(APPEAR_MILLIS))
    }
    Box(
        Modifier.graphicsLayer {
            alpha = shown.value
            translationY = (1f - shown.value) * APPEAR_RISE.toPx()
        },
    ) {
        content()
    }
}

@Composable
private fun GearRow(isDriving: Boolean) {
    val current = if (isDriving) "D" else "P"
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        GEARS.forEach { gear ->
            val on by animateFloatAsState(if (gear == current) 1f else 0f, label = "gear")
            Text(
                gear,
                modifier = Modifier
                    .background(ChagokColors.accent.copy(alpha = on), Shapes.control)
                    .padding(horizontal = Spacing.xs),
                color = lerp(ChagokColors.textDim, ChagokColors.onAccent, on),
                fontSize = TextSizes.subtitle,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// 계기판 거리. 달리는 장면 동안 0 에서 견본 거리까지 올라가고, 시동 장면으로 돌아가면 0 이다
@Composable
private fun TripDistance(shot: HowShot) {
    val km = remember { Animatable(0f) }
    LaunchedEffect(shot) {
        val end = HowStory.SAMPLE_KM.toFloat()
        when (shot.scene) {
            HowScene.IGNITION -> km.snapTo(0f)
            HowScene.DRIVING -> {
                km.snapTo(0f)
                km.animateTo(end, tween(shot.scene.millis, easing = LinearEasing))
            }
            HowScene.CHANGE, HowScene.PARKED -> km.animateTo(end)
        }
    }
    Text(
        stringResource(R.string.km_value_spaced, Formatters.km(km.value.toDouble())),
        color = ChagokColors.textSub,
        fontSize = TextSizes.subtitle,
        fontWeight = FontWeight.Bold,
    )
}

// 주행 화면 한 줄 (DrivingScreen 과 같은 색·문구)
@Composable
private fun DriveLine(category: Category, modifier: Modifier = Modifier) {
    Row(modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Box(
            Modifier
                .width(DRIVE_BAR_WIDTH)
                .fillMaxHeight()
                .background(category.color, Shapes.pill),
        )
        Text(stringResource(category.drivingLabelRes), fontSize = TextSizes.title, fontWeight = FontWeight.Bold)
    }
}

// Gleo 에게 하는 말. 아직 안 한 글자도 투명하게 자리를 잡아 줄이 바뀌며 흔들리지 않는다
@Composable
private fun GleoAsk(phrase: String, typed: Float) {
    val count = (phrase.length * typed).toInt()
    Row(
        Modifier
            .fillMaxWidth()
            .background(ChagokColors.gleo.copy(alpha = GLEO_TINT), Shapes.control)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Mic, contentDescription = null, tint = ChagokColors.gleo)
        Text(
            buildAnnotatedString {
                append(phrase.take(count))
                withStyle(SpanStyle(color = Color.Transparent)) { append(phrase.drop(count)) }
            },
            fontSize = TextSizes.subtitle,
            fontWeight = FontWeight.Bold,
        )
    }
}

// "방금 운행" 카드 조각
@Composable
private fun ArrivalCard(category: Category, place: String, modifier: Modifier) {
    val isPersonal = category == Category.PERSONAL
    Row(
        modifier
            .fillMaxWidth()
            .background(ChagokColors.cardStrong, Shapes.control)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = ChagokColors.green,
            modifier = Modifier.size(IconSizes.small),
        )
        Column {
            Text(
                if (isPersonal) stringResource(R.string.onboarding_how_card_personal) else place,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(
                    if (isPersonal) R.string.onboarding_how_card_sub_personal else R.string.onboarding_how_card_sub_business,
                ),
                color = ChagokColors.textSub,
            )
        }
    }
}

// 안내 문장 + 들어 보기
@Composable
private fun Speech(text: String, onListen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(ChagokColors.section, Shapes.control)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = ChagokColors.accent)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(stringResource(R.string.onboarding_quote, text))
            ListenButton(onListen)
        }
    }
}

@Composable
private fun ListenButton(onClick: () -> Unit) {
    Row(
        Modifier.pressable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = ChagokColors.accent)
        Text(stringResource(R.string.onboarding_how_listen), color = ChagokColors.accent, fontWeight = FontWeight.Bold)
    }
}
