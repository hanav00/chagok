package kr.chagok.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import kr.chagok.R
import kr.chagok.domain.model.EnergyKind
import kr.chagok.domain.model.Refuel
import kr.chagok.ui.components.MiniButton
import kr.chagok.ui.components.Section
import kr.chagok.ui.components.SectionTitle
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TouchSizes

/**
 * 주유·충전 금액: 차가 알아챈 주유·충전마다 "금액 말하기"로 금액을 받는다.
 * 법인카드 내역으로 따로 맞출 때는 "모름"으로 넘긴다
 */
@Composable
internal fun RefuelSection(
    state: RefuelTodoState,
    onSpeak: (refuelId: Long) -> Unit,
    onSkip: (refuelId: Long) -> Unit,
) {
    Section {
        SectionTitle(stringResource(R.string.refuel_title), count = state.refuels.size)
        Column {
            state.refuels.forEach { refuel ->
                HorizontalDivider(color = ChagokColors.divider)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RefuelInfo(refuel, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(TouchSizes.gap)) {
                        val isListening = refuel.id == state.listeningId
                        MiniButton(
                            text = stringResource(if (isListening) R.string.recent_listening else R.string.refuel_speak),
                            color = ChagokColors.accent,
                            onClick = { onSpeak(refuel.id) },
                        )
                        MiniButton(
                            text = stringResource(R.string.refuel_skip),
                            color = ChagokColors.textSub,
                            onClick = { onSkip(refuel.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RefuelInfo(refuel: Refuel, modifier: Modifier = Modifier) {
    val kind = stringResource(if (refuel.kind == EnergyKind.FUEL) R.string.refuel_fuel else R.string.refuel_charge)
    val detail = listOf(
        Formatters.day(refuel.date),
        Formatters.time(refuel.time),
        stringResource(R.string.refuel_percent, refuel.fromPercent, refuel.toPercent),
    ).joinToString(Formatters.SEPARATOR)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(kind, fontWeight = FontWeight.Bold)
        Text(detail, color = ChagokColors.textSub)
    }
}
