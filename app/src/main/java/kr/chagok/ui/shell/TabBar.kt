package kr.chagok.ui.shell

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TableView
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.ui.components.pressable
import kr.chagok.ui.theme.IconSizes
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes
import kr.chagok.ui.theme.TouchSizes

private val BADGE_SIZE = 28.dp
private val BADGE_OFFSET_X = 32.dp
private val BADGE_OFFSET_Y = (-4).dp
private val SELECTED_DOT = 6.dp

/** 앱 탭. 내보내기는 기록부 안에 있다 */
enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, Icons.Rounded.Home),
    TABLE(R.string.tab_table, Icons.Rounded.TableView),
    SIMULATOR(R.string.tab_simulator, Icons.Rounded.Savings),
    SETTINGS(R.string.tab_settings, Icons.Rounded.Settings),
}

/** 위쪽 탭 줄 (좁은 창). 홈 탭에는 사람이 골라야 하는 것의 개수 배지 */
@Composable
fun TabBar(selected: Tab, homeBadge: Int, onSelect: (Tab) -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Tab.entries.forEach { tab ->
                TabItem(
                    tab = tab,
                    isSelected = tab == selected,
                    badge = if (tab == Tab.HOME) homeBadge else 0,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        HorizontalDivider(color = ChagokColors.divider)
    }
}

/** 넓은 창은 탭을 헤더 안에 한 줄로 넣어, 홈이 세로를 덜 잃게 한다. 상자 없이 글자만, 고른 탭은 아래 보라 점 */
@Composable
fun HeaderTabs(selected: Tab, homeBadge: Int, onSelect: (Tab) -> Unit) {
    Row(
        modifier = Modifier.padding(start = Spacing.l),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Tab.entries.forEach { tab ->
            HeaderTabItem(
                tab = tab,
                isSelected = tab == selected,
                badge = if (tab == Tab.HOME) homeBadge else 0,
                onClick = { onSelect(tab) },
            )
        }
    }
}

@Composable
private fun TabItem(tab: Tab, isSelected: Boolean, badge: Int, onClick: () -> Unit, modifier: Modifier) {
    val color = if (isSelected) ChagokColors.text else ChagokColors.textSub
    Box(
        modifier = modifier
            .background(if (isSelected) ChagokColors.card else ChagokColors.window, Shapes.control)
            .tabSemantics(isSelected, badge, onClick)
            .padding(vertical = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(IconSizes.small))
            Text(stringResource(tab.label), color = color)
        }
        if (badge > 0) Badge(badge, Modifier.align(Alignment.TopCenter).offset(x = BADGE_OFFSET_X, y = BADGE_OFFSET_Y))
    }
}

@Composable
private fun HeaderTabItem(tab: Tab, isSelected: Boolean, badge: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(TouchSizes.headButton)
            .tabSemantics(isSelected, badge, onClick)
            .padding(horizontal = Spacing.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(tab.label),
            color = if (isSelected) ChagokColors.text else ChagokColors.textDim,
            fontWeight = FontWeight.SemiBold,
        )
        if (isSelected) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Spacing.xxs)
                    .size(SELECTED_DOT)
                    .background(ChagokColors.accent, Shapes.pill),
            )
        }
        if (badge > 0) Badge(badge, Modifier.align(Alignment.TopEnd).offset(x = Spacing.s, y = Spacing.xxs))
    }
}

@Composable
private fun Modifier.tabSemantics(isSelected: Boolean, badge: Int, onClick: () -> Unit): Modifier {
    val badgeDescription = stringResource(R.string.tab_badge_description, badge)
    return semantics(mergeDescendants = true) {
        selected = isSelected
        if (badge > 0) stateDescription = badgeDescription
    }.pressable(role = Role.Tab, onClick = onClick)
}

@Composable
private fun Badge(count: Int, modifier: Modifier) {
    Text(
        text = count.toString(),
        modifier = modifier
            .defaultMinSize(minWidth = BADGE_SIZE)
            .height(BADGE_SIZE)
            .background(ChagokColors.pending, Shapes.pill)
            .padding(horizontal = Spacing.xs)
            .clearAndSetSemantics {}, // 숫자는 탭 설명(stateDescription)으로 읽는다
        color = ChagokColors.onPending,
        fontSize = TextSizes.caption,
        fontWeight = FontWeight.ExtraBold,
    )
}
