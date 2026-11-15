package kr.chagok.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Density

/**
 * 기준 px → 차 화면 dp 배율. 기준 캔버스 1920×1080 의 분할 칸(620px)·넓게(1252px)가
 * Pleos 에뮬레이터(2560×1440, 160dpi)의 앱 창 853dp·1682dp 와 정확히 4/3 배다.
 * 화면 코드는 기준 px 숫자를 그대로 dp·sp 로 쓰고, 여기서 한 번에 키운다.
 */
private const val UI_SCALE = 4f / 3f

private val colorScheme = darkColorScheme(
    primary = ChagokColors.accent,
    onPrimary = ChagokColors.onAccent,
    background = ChagokColors.window,
    onBackground = ChagokColors.text,
    surface = ChagokColors.window,
    onSurface = ChagokColors.text,
    surfaceVariant = ChagokColors.cardStrong,
    onSurfaceVariant = ChagokColors.textSub,
    outline = ChagokColors.divider,
    error = ChagokColors.red,
)

/**
 * 한국어는 어절 단위로 줄을 바꾼다 ("총주행거/리"처럼 한 글자만 넘어가지 않게. Android 13 아래는 글자 단위).
 * 글꼴은 [AppFontFamily]. TextStyle 을 새로 만들어 style 로 넘기는 곳은 fontFamily 를 직접 넣어야 한다.
 */
private val baseTextStyle = TextStyle(
    color = ChagokColors.text,
    fontFamily = AppFontFamily,
    fontSize = TextSizes.body,
    lineBreak = LineBreak(LineBreak.Strategy.HighQuality, LineBreak.Strictness.Normal, LineBreak.WordBreak.Phrase),
)

@Composable
fun ChagokTheme(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(
            LocalDensity provides Density(density.density * UI_SCALE, density.fontScale),
            LocalContentColor provides ChagokColors.text,
            LocalTextStyle provides baseTextStyle,
            content = content,
        )
    }
}
