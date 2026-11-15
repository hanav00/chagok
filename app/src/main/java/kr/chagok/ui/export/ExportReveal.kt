package kr.chagok.ui.export

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay
import kr.chagok.R
import kr.chagok.domain.model.Category
import kr.chagok.domain.model.DistanceTotals
import kr.chagok.ui.components.GravityEasing
import kr.chagok.ui.components.OvershootEasing
import kr.chagok.ui.components.TripBlock
import kr.chagok.ui.components.flippingY
import kr.chagok.ui.components.growingAt
import kr.chagok.ui.components.staggered
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

private val REVEAL_WIDTH = 420.dp
private val REVEAL_MIN_HEIGHT = 300.dp
private val TOWER_HEIGHT = 250.dp
private val TOWER_GAP = Spacing.xs
private val TOWER_MAX_WIDTH = 56.dp
private val TOWER_BLOCK_GAP = Spacing.xxs
private val BOOK_WIDTH = 260.dp
private val BOOK_HEIGHT = 220.dp

private const val TOWER_STAGGER_MILLIS = 70

/** 탑이 많아도(한 달 30일) 솟는 물결은 이 시간 안에 끝난다 */
private const val TOWER_WAVE_MAX_MILLIS = 900
private const val TOWER_BLOCK_STAGGER_MILLIS = 120
private const val TOWER_GROW_MILLIS = 520
private const val HOLD_MILLIS = 600L
private const val MERGE_MILLIS = 520
private const val BOOK_POP_MILLIS = 520
private const val COUNT_MILLIS = 900
private const val FLIP_OUT_MILLIS = 240
private const val FLIP_IN_MILLIS = 300
private const val FLIP_EDGE_DEGREES = 90f

/** 모일 때 탑이 이만큼 가늘어지고, 끝 무렵 이 진행값부터 사라진다 */
private const val MERGE_SQUEEZE = 0.4f
private const val MERGE_FADE_FROM = 0.8f

/** 탑 안 쌓는 순서 (아래부터) */
private val TOWER_ORDER: List<Category?> = listOf(Category.COMMUTE, Category.BUSINESS, Category.PERSONAL, null)

private enum class RevealPhase { TOWERS, BOOK, QR }

/**
 * 내보내기 QR 이 뜨기 전 한 번: 날짜(달)마다 탑이 솟고, 가운데로 모여 기록부 한 권이 되고, 책이 뒤집혀 QR 이 된다.
 * "쌓인 기록이 한 권이 된다"를 먼저 보여 준다. 종류(이 달·올해)를 바꾸면 다시 한다.
 * 탑은 운행이 있는 날(달)만 세운다. 운행이 없으면 탑 없이 책부터.
 */
@Composable
internal fun ExportReveal(
    key: Any,
    towers: List<DistanceTotals>,
    bookTitle: String,
    tripCount: Int,
    qr: @Composable () -> Unit,
) {
    val filled = remember(towers) { towers.filter { it.total > 0 } }
    var phase by remember(key) { mutableStateOf(RevealPhase.TOWERS) }
    val stagger = towerStaggerMillis(filled.size)
    val growMillis = (filled.size - 1).coerceAtLeast(0) * stagger +
        TOWER_ORDER.size * TOWER_BLOCK_STAGGER_MILLIS + TOWER_GROW_MILLIS
    val grow = remember(key) { Animatable(0f) }
    val merge = remember(key) { Animatable(0f) }
    val book = remember(key) { Animatable(0f) }
    val count = remember(key) { Animatable(0f) }
    val flip = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        if (filled.isNotEmpty()) {
            grow.animateTo(growMillis.toFloat(), tween(growMillis, easing = LinearEasing))
            delay(HOLD_MILLIS)
            merge.animateTo(1f, tween(MERGE_MILLIS, easing = GravityEasing))
        }
        phase = RevealPhase.BOOK
        book.animateTo(1f, tween(BOOK_POP_MILLIS, easing = OvershootEasing))
        count.animateTo(tripCount.toFloat(), tween(COUNT_MILLIS))
        delay(HOLD_MILLIS)
        flip.animateTo(FLIP_EDGE_DEGREES, tween(FLIP_OUT_MILLIS, easing = GravityEasing))
        phase = RevealPhase.QR
        flip.snapTo(-FLIP_EDGE_DEGREES)
        flip.animateTo(0f, tween(FLIP_IN_MILLIS))
    }
    Box(Modifier.width(REVEAL_WIDTH).heightIn(min = REVEAL_MIN_HEIGHT), contentAlignment = Alignment.Center) {
        when (phase) {
            RevealPhase.TOWERS -> Towers(filled, stagger, { grow.value }, merge.value)
            RevealPhase.BOOK -> Book(bookTitle, count.value.toInt(), Modifier.graphicsLayer {
                val pop = book.value
                scaleX = lerp(0.4f, 1f, pop)
                scaleY = lerp(1.3f, 1f, pop)
                alpha = pop.coerceIn(0f, 1f)
            }.flippingY(flip.value))
            RevealPhase.QR -> Box(Modifier.flippingY(flip.value)) { qr() }
        }
    }
}

// 탑 줄. 솟을 때는 시계 하나로 탑마다 차례로, 모일 때는 가운데로 가늘어지며 사라진다
@Composable
private fun Towers(towers: List<DistanceTotals>, stagger: Int, clock: () -> Float, merge: Float) {
    if (towers.isEmpty()) return
    val maxKm = towers.maxOfOrNull { it.total }?.takeIf { it > 0 } ?: 1.0
    BoxWithConstraints(Modifier.fillMaxWidth().height(TOWER_HEIGHT)) {
        val count = towers.size
        val column = ((maxWidth - TOWER_GAP * (count - 1)) / count).coerceAtMost(TOWER_MAX_WIDTH)
        // 탑 줄은 가운데에 모여 있어, 줄 가운데가 곧 창 가운데다
        val rowCenter = (column * count + TOWER_GAP * (count - 1)) / 2
        Row(
            Modifier.fillMaxWidth().fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(TOWER_GAP, Alignment.CenterHorizontally),
        ) {
            towers.forEachIndexed { index, totals ->
                val toCenter = rowCenter - ((column + TOWER_GAP) * index + column / 2)
                Column(
                    modifier = Modifier
                        .width(column)
                        .fillMaxHeight()
                        .graphicsLayer {
                            translationX = toCenter.toPx() * merge
                            scaleX = 1f - MERGE_SQUEEZE * merge
                            alpha = 1f - ((merge - MERGE_FADE_FROM) / (1f - MERGE_FADE_FROM)).coerceIn(0f, 1f)
                        },
                    verticalArrangement = Arrangement.spacedBy(TOWER_BLOCK_GAP, Alignment.Bottom),
                ) {
                    TOWER_ORDER.withIndex().reversed().forEach { (order, category) ->
                        val km = totals.kmOf(category)
                        if (km <= 0) return@forEach
                        val delay = index * stagger + order * TOWER_BLOCK_STAGGER_MILLIS
                        TripBlock(
                            category = category,
                            isGap = category == null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(heightOf(km, maxKm))
                                .growingAt { staggered(clock(), delay, TOWER_GROW_MILLIS) },
                        )
                    }
                }
            }
        }
    }
}

private fun towerStaggerMillis(count: Int): Int =
    if (count <= 1) TOWER_STAGGER_MILLIS else minOf(TOWER_STAGGER_MILLIS, TOWER_WAVE_MAX_MILLIS / (count - 1))

private fun heightOf(km: Double, maxKm: Double): Dp =
    (TOWER_HEIGHT - TOWER_BLOCK_GAP * TOWER_ORDER.size) * (km / maxKm).toFloat()

// 기록부 한 권: 업무 보라 블록에 기간 · "운행기록부" · 건수
@Composable
private fun Book(title: String, count: Int, modifier: Modifier) {
    TripBlock(
        category = Category.BUSINESS,
        isGap = false,
        shape = Shapes.blockLarge,
        modifier = modifier.size(BOOK_WIDTH, BOOK_HEIGHT),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(title, color = ChagokColors.onBusiness, fontSize = TextSizes.hero, fontWeight = FontWeight.Black)
            Text(
                stringResource(R.string.export_book_title),
                color = ChagokColors.onBusiness,
                fontSize = TextSizes.title,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                stringResource(R.string.count_value, count),
                color = ChagokColors.onBusiness,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
