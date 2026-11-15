package kr.chagok.ui.export

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kotlin.random.Random

private const val QR_CELLS = 29
private const val FINDER_CELLS = 7
private const val QUIET_CELLS = 2 // 둘레 흰 여백
private const val DARK_THRESHOLD = 0.52 // 난수가 이보다 크면 검은 칸
private val QR_SIZE = 250.dp

// QR 은 어두운 테마여도 흰 바탕에 검은 칸이어야 폰 카메라가 읽는다
private val QR_LIGHT = ChagokColors.white
private val QR_DARK = ChagokColors.onBright

// TODO: 실제 QR·엑셀 생성
/**
 * 엑셀 받기 QR 자리에 둔 그림. 데이터를 담지 않아 찍어도 받을 수 없으니 그렇다고 아래에 밝힌다.
 * [seed] 가 같으면(같은 달·같은 운행 수) 같은 그림이다.
 */
@Composable
internal fun ExportQr(seed: String) {
    val cells = remember(seed) { placeholderCells(seed) }
    val label = stringResource(R.string.export_qr_label)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Canvas(
            Modifier
                .size(QR_SIZE)
                .clip(Shapes.control)
                .semantics { contentDescription = label },
        ) {
            val cell = size.width / (QR_CELLS + QUIET_CELLS * 2)
            val origin = QUIET_CELLS * cell
            drawRect(QR_LIGHT)
            cells.forEach { (x, y) -> drawCells(QR_DARK, origin, cell, x, y, 1) }
            val far = QR_CELLS - FINDER_CELLS
            listOf(0 to 0, far to 0, 0 to far).forEach { (x, y) -> drawFinder(origin, cell, x, y) }
        }
        Text(stringResource(R.string.export_qr_placeholder), color = ChagokColors.textSub)
    }
}

// 모서리 찾기 무늬: 7칸 검정 · 5칸 흰색 · 3칸 검정
private fun DrawScope.drawFinder(origin: Float, cell: Float, x: Int, y: Int) {
    drawCells(QR_DARK, origin, cell, x, y, FINDER_CELLS)
    drawCells(QR_LIGHT, origin, cell, x + 1, y + 1, FINDER_CELLS - 2)
    drawCells(QR_DARK, origin, cell, x + 2, y + 2, FINDER_CELLS - 4)
}

private fun DrawScope.drawCells(color: Color, origin: Float, cell: Float, x: Int, y: Int, count: Int) {
    drawRect(color, topLeft = Offset(origin + x * cell, origin + y * cell), size = Size(count * cell, count * cell))
}

/** 찾기 무늬 자리를 빼고 의사 난수로 채운 검은 칸 (x, y) */
private fun placeholderCells(seed: String): List<Pair<Int, Int>> {
    val random = Random(seed.hashCode()) // String.hashCode 는 JVM 명세로 고정이라 같은 seed 면 같은 그림이다
    val cells = mutableListOf<Pair<Int, Int>>()
    for (y in 0 until QR_CELLS) {
        for (x in 0 until QR_CELLS) {
            if (!isInFinder(x, y) && random.nextDouble() > DARK_THRESHOLD) cells += x to y
        }
    }
    return cells
}

// 왼쪽 위·오른쪽 위·왼쪽 아래 찾기 무늬와 그 둘레 한 칸 (오른쪽 아래는 비어 있다)
private fun isInFinder(x: Int, y: Int): Boolean {
    fun isNearEdge(v: Int) = v < FINDER_CELLS + 1 || v > QR_CELLS - FINDER_CELLS - 2
    val isBottomRight = x > QR_CELLS - FINDER_CELLS - 2 && y > QR_CELLS - FINDER_CELLS - 2
    return isNearEdge(x) && isNearEdge(y) && !isBottomRight
}
