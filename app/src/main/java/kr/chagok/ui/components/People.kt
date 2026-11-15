package kr.chagok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kr.chagok.R
import kr.chagok.domain.model.Person
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes
import kr.chagok.ui.theme.TouchSizes

private val CHECK_ICON = 22.dp

enum class AvatarSize(val size: Dp, val text: TextUnit) {
    Large(68.dp, TextSizes.headline),
    Medium(56.dp, TextSizes.title),
    Small(40.dp, TextSizes.body),
}

/**
 * 화면에 보일 사람 이름. 이름 없이 등록한 사람은 저장된 이름이 비어 있어 얼굴 원 글자로 정한다:
 * 이 화면을 쓰는 분은 "나", 그 밖은 "이름 없음 2". 기록부 ④성명에는 이 값 대신 "이름 입력 필요"가 나간다.
 */
@Composable
fun personName(name: String, initial: String): String {
    val me = stringResource(R.string.onboarding_me)
    return when {
        name.isNotBlank() -> name
        initial == me -> me
        else -> stringResource(R.string.person_unnamed, initial)
    }
}

@Composable
fun personName(person: Person): String = personName(person.name, person.initial)

/** 사람 얼굴 원: 회색 바탕 + 흰 글자 */
@Composable
fun Avatar(initial: String, modifier: Modifier = Modifier, size: AvatarSize = AvatarSize.Large) {
    Box(
        modifier = modifier
            .size(size.size)
            .background(ChagokColors.avatar, Shapes.pill),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, color = ChagokColors.text, fontSize = size.text, fontWeight = FontWeight.ExtraBold)
    }
}

/**
 * "누가 탔나요?" 얼굴 1탭 줄. 방금 운행·미지정 목록·운행 상세가 같이 쓴다.
 * 공용차는 시동마다 사람을 비워 두고, 안 고른 운행은 나중에 여기서 채운다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FacePicker(people: List<Person>, selectedId: Long?, onPick: (Long) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        people.forEach { person ->
            val isOn = person.id == selectedId
            Row(
                modifier = Modifier
                    .height(TouchSizes.segment)
                    .selectedFrame(isOn, Shapes.pill, idleBackground = ChagokColors.cardStrong)
                    .semantics { selected = isOn }
                    .pressable(role = Role.RadioButton) { onPick(person.id) }
                    .padding(start = Spacing.xs, end = Spacing.m),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(person.initial, size = AvatarSize.Small)
                Text(personName(person), fontWeight = FontWeight.Bold)
                if (isOn) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = ChagokColors.accent,
                        modifier = Modifier.size(CHECK_ICON),
                    )
                }
            }
        }
    }
}
