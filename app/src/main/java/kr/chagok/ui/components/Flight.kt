package kr.chagok.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** 천천히 떠서 빨라졌다가 내려앉는다 */
val FlightEasing = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)

/**
 * 한 화면 안에서 무언가가 이 자리에서 저 자리로 날아갈 때, 출발·도착 자리를 잰다.
 * 자리는 [flightRoot] 를 단 상자 기준이라, 날아가는 그림도 그 상자 안에 겹쳐 그린다.
 */
@Stable
class FlightSpots {
    private var root by mutableStateOf(Offset.Zero)
    private val spots = mutableStateMapOf<Any, Rect>()

    internal fun setRoot(bounds: Rect) {
        root = bounds.topLeft
    }

    internal fun set(key: Any, bounds: Rect) {
        spots[key] = bounds
    }

    operator fun get(key: Any): Rect? = spots[key]?.translate(-root)
}

fun Modifier.flightRoot(spots: FlightSpots): Modifier = onGloballyPositioned { spots.setRoot(it.boundsInRoot()) }

fun Modifier.flightSpot(spots: FlightSpots, key: Any): Modifier = onGloballyPositioned { spots.set(key, it.boundsInRoot()) }

/**
 * [from] 자리에 놓인 그림을 [to] 가운데로 옮긴다. [progress] 0 → 1.
 * 끝에서 [endScaleX]·[endScaleY] 배가 되고, 가는 동안 [arc] 만큼 위로 솟았다가 내려온다.
 */
fun Modifier.flying(
    from: Rect,
    to: Rect,
    progress: Float,
    endScaleX: Float,
    arc: Dp = 0.dp,
    endScaleY: Float = endScaleX,
): Modifier = offset { IntOffset(from.left.roundToInt(), from.top.roundToInt()) }
    .graphicsLayer {
        translationX = (to.center.x - from.center.x) * progress
        translationY = (to.center.y - from.center.y) * progress - arc.toPx() * sin(PI * progress).toFloat()
        scaleX = lerp(1f, endScaleX, progress)
        scaleY = lerp(1f, endScaleY, progress)
        transformOrigin = TransformOrigin.Center
    }
