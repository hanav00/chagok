package kr.chagok.ui.guide

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.ui.components.ActionButton
import kr.chagok.ui.components.pressable
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes

private val STAGE_HEIGHT = 270.dp
private val TAB_BAR_HEIGHT = 6.dp
private const val STAGE_WEIGHT = 1.3f // 넓게에서 무대를 설명보다 넓게

/** "이렇게 기록돼요"의 장면. 운행 하나가 이 순서로 흘러간다 */
internal enum class HowScene(
    @StringRes val tab: Int,
    @StringRes val title: Int,
    @StringRes val note: Int,
    val millis: Int,
) {
    IGNITION(
        R.string.onboarding_how_tab_ignition,
        R.string.onboarding_how_ignition,
        R.string.onboarding_how_ignition_note,
        3_500,
    ),
    DRIVING(
        R.string.onboarding_how_tab_driving,
        R.string.onboarding_how_driving,
        R.string.onboarding_how_commute_note,
        3_500,
    ),

    /** Gleo 가 없는 차에는 없다 */
    CHANGE(
        R.string.onboarding_how_tab_change,
        R.string.onboarding_how_change,
        R.string.onboarding_how_change_note,
        4_500,
    ),
    PARKED(
        R.string.onboarding_how_tab_parked,
        R.string.onboarding_how_parked,
        R.string.onboarding_how_card_note,
        4_500,
    ),
}

/**
 * 장면을 차례로 넘긴다. 들어오면 한 번 흐르고 마지막 장면에서 멈춘다. 장면마다 실제 음성 안내가 저절로 나온다.
 * 장면 이름을 누르면 그 장면에서 멈춘다.
 */
@Stable
private class HowPlayer(val scenes: List<HowScene>) {
    var index by mutableIntStateOf(0)
        private set
    var take by mutableIntStateOf(0)
        private set
    var isPlaying by mutableStateOf(true)
        private set

    /** 지금 장면이 흘러간 정도 (0 → 1). 위 막대를 채운다 */
    val progress = Animatable(0f)

    val shot: HowShot get() = HowShot(scenes[index], take)

    fun replay() {
        index = 0
        take++
        isPlaying = true
    }

    fun pick(target: Int) {
        index = target
        take++
        isPlaying = false
    }

    /** 장면 하나를 시작한다: 안내를 말하고, 흐르는 중이면 다 흐른 뒤 다음 장면으로 넘긴다 (마지막이면 멈춘다) */
    suspend fun run(onVoice: (HowScene) -> Unit) {
        onVoice(scenes[index])
        if (!isPlaying) {
            progress.snapTo(1f)
            return
        }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(scenes[index].millis, easing = LinearEasing))
        if (index < scenes.lastIndex) index++ else isPlaying = false
    }
}

/** 이렇게 기록돼요: 견본 운행 하나를 한 무대에서 장면 하나씩 보여 준다 (첫 실행 알아보기 · 설정 사용법 다시 보기) */
@Composable
fun HowContent(story: HowStory, isWide: Boolean, onSpeak: (String) -> Unit) {
    val player = remember(story.hasGleo) {
        HowPlayer(HowScene.entries.filter { story.hasGleo || it != HowScene.CHANGE })
    }
    // 멈춤(isPlaying)으로는 다시 돌지 않는다. 마지막 장면이 끝날 때 안내를 또 말하지 않게
    LaunchedEffect(player, player.index, player.take) {
        player.run { scene ->
            when (scene) {
                HowScene.IGNITION -> onSpeak(story.ignition)
                HowScene.PARKED -> onSpeak(story.arrival)
                HowScene.DRIVING, HowScene.CHANGE -> Unit
            }
        }
    }
    SceneTabs(player, isWide)
    val scene = player.scenes[player.index]
    if (isWide) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            HowStage(player.shot, story, onSpeak, Modifier.weight(STAGE_WEIGHT).height(STAGE_HEIGHT))
            Caption(scene, isWide, player::replay, Modifier.weight(1f).height(STAGE_HEIGHT))
        }
    } else {
        HowStage(player.shot, story, onSpeak, Modifier.fillMaxWidth().height(STAGE_HEIGHT))
        Caption(scene, isWide, player::replay, Modifier.fillMaxWidth())
    }
}

// 장면 이름표 줄. 지난 장면은 꽉, 지금 장면은 흘러간 만큼 막대가 찬다. 좁은 창에서는 넷이 한 줄에 들게 작은 글자
@Composable
private fun SceneTabs(player: HowPlayer, isWide: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        player.scenes.forEachIndexed { i, scene ->
            val isCurrent = i == player.index
            Column(
                Modifier
                    .weight(1f)
                    .pressable(role = Role.Tab) { player.pick(i) }
                    .padding(vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(TAB_BAR_HEIGHT)
                        .background(ChagokColors.cardStrong, Shapes.pill)
                        .drawBehind {
                            val filled = when {
                                i < player.index -> 1f
                                isCurrent -> player.progress.value
                                else -> 0f
                            }
                            drawRoundRect(
                                ChagokColors.accent,
                                size = Size(size.width * filled, size.height),
                                cornerRadius = CornerRadius(size.height / 2),
                            )
                        },
                )
                Text(
                    stringResource(R.string.onboarding_how_tab, i + 1, stringResource(scene.tab)),
                    color = if (isCurrent) ChagokColors.text else ChagokColors.textDim,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    fontSize = if (isWide) TextSizes.body else TextSizes.caption,
                )
            }
        }
    }
}

// 지금 장면의 설명 한 덩이 + 처음부터 보기. 넓게에서는 무대 높이에 맞춰 버튼을 아래에 붙인다
@Composable
private fun Caption(scene: HowScene, isWide: Boolean, onReplay: () -> Unit, modifier: Modifier) {
    Column(
        modifier,
        verticalArrangement = if (isWide) Arrangement.SpaceBetween else Arrangement.spacedBy(Spacing.m),
    ) {
        AnimatedContent(scene, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "how-caption") { shown ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(shown.title), fontSize = TextSizes.title, fontWeight = FontWeight.ExtraBold)
                Text(
                    stringResource(shown.note),
                    color = ChagokColors.textSub,
                    fontSize = if (isWide) TextSizes.subtitle else TextSizes.body,
                )
            }
        }
        ActionButton(stringResource(R.string.onboarding_how_play), onClick = onReplay, icon = Icons.Rounded.PlayCircle)
    }
}
