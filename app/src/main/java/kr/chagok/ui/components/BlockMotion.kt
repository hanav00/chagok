package kr.chagok.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp

private const val FALL_MILLIS = 380
private const val IMPACT_MILLIS = 300
private const val FLIP_OUT_MILLIS = 200
private const val FLIP_IN_MILLIS = 260
private const val FLIP_EDGE_DEGREES = 90f
private const val CAMERA_DISTANCE = 12f

// 착지 때 눌리는 정도. 아래 블록은 같은 충격을 이만큼 덜 받는다
private const val SQUASH_Y = 0.2f
private const val SQUASH_X = 0.06f
private const val THUD_RATIO = 0.5f
private const val REBOUND = -0.25f

/** 떨어질 때 점점 빨라진다 */
val GravityEasing = CubicBezierEasing(0.55f, 0f, 1f, 0.45f)

/** 목표를 조금 지나쳤다 돌아온다 (들어와 꽂히는 블록, 솟는 탑) */
val OvershootEasing = CubicBezierEasing(0.3f, 0f, 0.2f, 1.2f)

/** 한 진행값 안에서 떨어지는 몫. 나머지가 착지 충격이다 */
private const val FALL_SHARE = 0.6f

/**
 * 블록 하나가 위에서 떨어져 바닥에 닿고, 눌렸다 펴지는 움직임.
 * [fall] 1 은 위에 떠 있어 안 보임, 0 은 제자리. [impact] 0 → 1 이 착지 뒤 눌림.
 */
@Stable
class BlockDrop(isLanded: Boolean) {
    internal val fall = Animatable(if (isLanded) 0f else 1f)
    internal val impact = Animatable(if (isLanded) 1f else 0f)

    val isLanded: Boolean get() = fall.value == 0f && impact.value == 1f

    suspend fun play() {
        fall.animateTo(0f, tween(FALL_MILLIS, easing = GravityEasing))
        impact.snapTo(0f)
        impact.animateTo(1f, tween(IMPACT_MILLIS, easing = LinearEasing))
    }

    /** 다시 떨어뜨릴 수 있게 위로 되돌린다 */
    suspend fun reset() {
        fall.snapTo(1f)
        impact.snapTo(0f)
    }

    /** 떨어지지 않고 제자리에서 충격만 (빈칸이 메워져 위 블록이 내려앉을 때) */
    suspend fun bump() {
        impact.snapTo(0f)
        impact.animateTo(1f, tween(IMPACT_MILLIS, easing = LinearEasing))
    }
}

@Composable
fun rememberBlockDrop(key: Any?, isLanded: Boolean = false): BlockDrop = remember(key) { BlockDrop(isLanded) }

/** [distance] 위에서 떨어져 바닥 기준으로 눌렸다 펴진다 */
fun Modifier.dropping(drop: BlockDrop, distance: Dp): Modifier = graphicsLayer {
    val fall = drop.fall.value
    translationY = -distance.toPx() * fall
    alpha = (1f - fall) * 2f
    val squash = squashOf(drop.impact.value)
    scaleY = 1f - SQUASH_Y * squash
    scaleX = 1f + SQUASH_X * squash
    transformOrigin = TransformOrigin(0.5f, 1f)
}

/** 위에 블록이 떨어질 때 아래 블록이 같이 눌린다 */
fun Modifier.thudding(drop: BlockDrop): Modifier = graphicsLayer {
    scaleY = 1f - SQUASH_Y * THUD_RATIO * squashOf(drop.impact.value)
    transformOrigin = TransformOrigin(0.5f, 1f)
}

/**
 * 진행값 하나(0 → 1)로 떨어짐과 착지 충격을 같이 그린다.
 * 여러 블록을 Animatable 하나로 차례로 움직일 때 쓴다 ([staggered] 로 블록마다 진행값을 나눈다).
 */
fun Modifier.droppingAt(progress: () -> Float, distance: Dp): Modifier = graphicsLayer {
    val p = progress()
    val fall = 1f - GravityEasing.transform((p / FALL_SHARE).coerceIn(0f, 1f))
    translationY = -distance.toPx() * fall
    alpha = ((1f - fall) * 2f).coerceIn(0f, 1f)
    val squash = squashOf((p - FALL_SHARE) / (1f - FALL_SHARE))
    scaleY = 1f - SQUASH_Y * squash
    scaleX = 1f + SQUASH_X * squash
    transformOrigin = TransformOrigin(0.5f, 1f)
}

/** 바닥에서 솟는다. 진행값 0 → 1, 끝에서 살짝 넘쳤다 돌아온다 */
fun Modifier.growingAt(progress: () -> Float): Modifier = graphicsLayer {
    val p = progress()
    scaleY = OvershootEasing.transform(p)
    alpha = if (p > 0f) 1f else 0f
    transformOrigin = TransformOrigin(0.5f, 1f)
}

/** 전체 시계 [nowMillis] 에서, [delayMillis] 뒤에 [durationMillis] 동안 움직이는 블록 하나의 진행값 */
fun staggered(nowMillis: Float, delayMillis: Int, durationMillis: Int): Float =
    ((nowMillis - delayMillis) / durationMillis).coerceIn(0f, 1f)

/** 화면이 처음 뜰 때 한 번 도는 순서의 시계 (ms). 이미 본 화면은 끝난 시각에서 시작한다 */
@Stable
class IntroClock(private val totalMillis: Int, isDone: Boolean) {
    private val clock = Animatable(if (isDone) totalMillis.toFloat() else 0f)

    suspend fun play() {
        val left = totalMillis - clock.value.toInt()
        if (left > 0) clock.animateTo(totalMillis.toFloat(), tween(left, easing = LinearEasing))
    }

    /** [delayMillis] 뒤에 [durationMillis] 동안 움직이는 부분의 진행값 */
    fun at(delayMillis: Int, durationMillis: Int): Float = staggered(clock.value, delayMillis, durationMillis)
}

@Composable
fun rememberIntroClock(key: Any?, totalMillis: Int, isDone: Boolean): IntroClock =
    remember(key) { IntroClock(totalMillis, isDone) }

/** 착지 충격 0 → 1 동안 눌림 1 → 살짝 늘어남 → 제자리 0 */
fun squashOf(t: Float): Float = when {
    t <= 0f || t >= 1f -> 0f
    t < 0.3f -> t / 0.3f
    t < 0.7f -> lerp(1f, REBOUND, (t - 0.3f) / 0.4f)
    else -> lerp(REBOUND, 0f, (t - 0.7f) / 0.3f)
}

/** 값이 바뀌면 카드처럼 뒤집혀 바뀐다. 처음 값은 뒤집지 않는다 */
@Stable
class Flip<T>(initial: T) {
    var shown by mutableStateOf(initial)
        internal set
    internal val degrees = Animatable(0f)
}

@Composable
fun <T> rememberFlip(value: T): Flip<T> {
    val flip = remember { Flip(value) }
    LaunchedEffect(value) {
        if (value == flip.shown) return@LaunchedEffect
        flip.degrees.animateTo(FLIP_EDGE_DEGREES, tween(FLIP_OUT_MILLIS, easing = GravityEasing))
        flip.shown = value
        flip.degrees.snapTo(-FLIP_EDGE_DEGREES)
        flip.degrees.animateTo(0f, tween(FLIP_IN_MILLIS))
    }
    return flip
}

/** 가로축으로 뒤집기 (블록) */
fun Modifier.flippingX(flip: Flip<*>): Modifier = graphicsLayer {
    rotationX = flip.degrees.value
    cameraDistance = CAMERA_DISTANCE * density
}

/** 세로축으로 뒤집기 (책 → QR) */
fun Modifier.flippingY(degrees: Float): Modifier = graphicsLayer {
    rotationY = degrees
    cameraDistance = CAMERA_DISTANCE * density
}
