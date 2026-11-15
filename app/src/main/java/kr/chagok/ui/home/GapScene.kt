package kr.chagok.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.Work
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kr.chagok.R
import kr.chagok.domain.model.Category
import kr.chagok.ui.components.BlockDrop
import kr.chagok.ui.components.GravityEasing
import kr.chagok.ui.components.OvershootEasing
import kr.chagok.ui.components.TripBlock
import kr.chagok.ui.components.color
import kr.chagok.ui.components.departureIcon
import kr.chagok.ui.components.pressable
import kr.chagok.ui.components.shortLabelRes
import kr.chagok.ui.components.thudding
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes
import kr.chagok.ui.theme.TouchSizes
import kr.chagok.ui.theme.WindowWidths

private val STACK_WIDTH = 420.dp
private val BLOCK_HEIGHT = 72.dp
private val BLOCK_GAP = Spacing.xs
private val KM_SIZE = 120.sp
private val KM_UNIT_SIZE = 48.sp
private val CHECK_SIZE = 110.dp
private val PICK_MIN_WIDTH = 200.dp

/** 빈칸 위 블록이 떠 있는 높이와 메울 블록이 들어오는 거리 */
private val FLOAT_HEIGHT = 26.dp
private val FILL_DISTANCE = 700.dp

private const val FLOAT_MILLIS = 900
private const val BLINK_MILLIS = 600
private const val BLINK_MIN_ALPHA = 0.35f
private const val FILL_MILLIS = 560
private const val SETTLE_MILLIS = 360
private const val DONE_HOLD_MILLIS = 1_100L

/**
 * 기록 안 된 주행: 그날 탑 가운데가 점선 빈칸이고, 그 위 블록들이 떠 있다.
 * 업무·개인을 누르면 그 색 블록이 옆에서 들어와 꽂히고, 떠 있던 블록이 내려앉은 뒤 "계기판이 다시 이어졌어요".
 * 분류는 움직임이 끝날 때 저장한다. 그 전에 화면을 떠나도 저장은 한다.
 */
@Composable
internal fun GapScene(scene: GapSceneState, onClassify: (tripId: Long, Category) -> Unit) {
    var picked by remember(scene.trip.id) { mutableStateOf<Category?>(null) }
    var isDone by remember(scene.trip.id) { mutableStateOf(false) }
    val fill = remember(scene.trip.id) { Animatable(1f) }
    val lift = remember(scene.trip.id) { Animatable(1f) }
    val settle = remember(scene.trip.id) { BlockDrop(isLanded = true) }
    LaunchedEffect(picked) {
        val category = picked ?: return@LaunchedEffect
        try {
            fill.animateTo(0f, tween(FILL_MILLIS, easing = OvershootEasing))
            lift.animateTo(0f, tween(SETTLE_MILLIS, easing = GravityEasing))
            settle.bump()
            isDone = true
            delay(DONE_HOLD_MILLIS)
        } finally {
            onClassify(scene.trip.id, category)
        }
    }
    val floating = rememberInfiniteTransition(label = "gap").let { transition ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(FLOAT_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "float",
        )
    }
    val blink = rememberInfiniteTransition(label = "gapBlink")
        .animateFloat(1f, BLINK_MIN_ALPHA, infiniteRepeatable(tween(BLINK_MILLIS), RepeatMode.Reverse), label = "blink")

    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = Spacing.sectionVertical)) {
        val stack: @Composable () -> Unit = {
            GapStack(scene, picked, fill.value, lift.value * floating.value, blink.value, settle)
        }
        val info: @Composable () -> Unit = {
            GapInfo(scene, isDone, isPicking = picked == null, onPick = { picked = it })
        }
        if (maxWidth >= WindowWidths.wide) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxxl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                stack()
                info()
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                stack()
                info()
            }
        }
    }
}

// 아래부터 쌓은 그날 탑. 빈칸 위 블록은 [lift] 만큼 떠 있다가 메워지면 내려앉고, 아래 블록은 그때 눌린다
@Composable
private fun GapStack(
    scene: GapSceneState,
    picked: Category?,
    fill: Float,
    lift: Float,
    blink: Float,
    settle: BlockDrop,
) {
    val gapIndex = scene.dayStack.indexOfFirst { it.id == scene.trip.id }
    Column(Modifier.widthIn(max = STACK_WIDTH).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BLOCK_GAP)) {
        scene.dayStack.indices.reversed().forEach { index ->
            val block = scene.dayStack[index]
            val modifier = Modifier.fillMaxWidth().height(BLOCK_HEIGHT)
            when {
                index == gapIndex -> Box(modifier.thudding(settle)) {
                    TripBlock(
                        category = null,
                        isGap = true,
                        shape = Shapes.blockLarge,
                        modifier = Modifier.matchParentSize().graphicsLayer { alpha = blink },
                    ) {
                        Text(
                            "?",
                            color = ChagokColors.pendingText,
                            fontSize = TextSizes.headline,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    picked?.let { category ->
                        TripBlock(
                            category = category,
                            isGap = false,
                            shape = Shapes.blockLarge,
                            modifier = Modifier.matchParentSize().graphicsLayer {
                                translationX = FILL_DISTANCE.toPx() * fill
                                alpha = 1f - fill
                            },
                        ) { BlockLabel(category.departureIcon, gapLabel(category, block.km)) }
                    }
                }
                index > gapIndex -> TripBlock(
                    block.category,
                    block.isGap,
                    shape = Shapes.blockLarge,
                    modifier = modifier.graphicsLayer { translationY = -FLOAT_HEIGHT.toPx() * lift },
                ) { BlockLabel(iconOf(block), labelOf(block)) }
                else -> TripBlock(
                    block.category,
                    block.isGap,
                    shape = Shapes.blockLarge,
                    modifier = modifier.thudding(settle),
                ) { BlockLabel(iconOf(block), labelOf(block)) }
            }
        }
    }
}

// 오른쪽(좁으면 아래): 큰 거리 숫자 → 고르기. 메운 뒤에는 확인 표시와 한 줄
@Composable
private fun GapInfo(scene: GapSceneState, isDone: Boolean, isPicking: Boolean, onPick: (Category) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        if (isDone) {
            Icon(Icons.Rounded.CheckCircle, null, tint = ChagokColors.business, modifier = Modifier.size(CHECK_SIZE))
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Formatters.km(scene.trip.km),
                    color = ChagokColors.pending,
                    fontSize = KM_SIZE,
                    fontWeight = FontWeight.Black,
                    lineHeight = KM_SIZE,
                )
                Text(
                    stringResource(R.string.km_unit),
                    color = ChagokColors.pending,
                    fontSize = KM_UNIT_SIZE,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Text(
            stringResource(if (isDone) R.string.home_gap_done else R.string.home_gap_scene_sub),
            color = ChagokColors.textSub,
            fontSize = TextSizes.headline,
            fontWeight = FontWeight.Bold,
        )
        if (isPicking) {
            Row(horizontalArrangement = Arrangement.spacedBy(TouchSizes.gap)) {
                Category.GAP_CHOICES.forEach { category -> PickButton(category) { onPick(category) } }
            }
        }
    }
}

@Composable
private fun PickButton(category: Category, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .widthIn(min = PICK_MIN_WIDTH)
            .height(TouchSizes.button)
            .background(category.color, Shapes.control)
            .pressable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BlockLabel(category.departureIcon, stringResource(category.shortLabelRes))
    }
}

@Composable
private fun BlockLabel(icon: ImageVector, text: String) {
    Icon(icon, null, tint = ChagokColors.onBright)
    Text(
        text,
        color = ChagokColors.onBright,
        fontSize = TextSizes.subtitle,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun iconOf(block: TripBlockState): ImageVector = when (block.category) {
    Category.COMMUTE -> Icons.Rounded.HomeWork
    Category.PERSONAL -> Icons.Rounded.DirectionsCar
    else -> Icons.Rounded.Flag
}

@Composable
private fun labelOf(block: TripBlockState): String =
    block.placeName ?: block.category?.let { stringResource(it.shortLabelRes) } ?: stringResource(R.string.trip_gap)

@Composable
private fun gapLabel(category: Category, km: Double): String =
    listOf(stringResource(category.shortLabelRes), stringResource(R.string.km_value_spaced, Formatters.km(km)))
        .joinToString(Formatters.SEPARATOR)
