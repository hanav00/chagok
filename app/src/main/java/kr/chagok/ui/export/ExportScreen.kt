package kr.chagok.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.chagok.R
import kr.chagok.domain.model.VehicleField
import kr.chagok.ui.components.HeaderButton
import kr.chagok.ui.components.ScreenColumn
import kr.chagok.ui.components.ScreenPadding
import kr.chagok.ui.components.SegmentedControl
import kr.chagok.ui.components.WideScreenPadding
import kr.chagok.ui.format.Formatters
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes
import kr.chagok.ui.vehicle.labelRes
import java.time.YearMonth

/**
 * 기록부 > 내보내기: 폰으로 QR 을 찍어 엑셀로 받는다 (서버 없음). 한 달·한 해에 한 번 쓰는 기능이라 탭을 따로 두지 않는다.
 * 월별은 회사가 보관하는 기록부 원본, 연간은 "업무용승용차 관련비용 명세서"에 옮길 값(세무대리인용)이다.
 * PIN 을 받은 뒤에만 열리므로(잠기면 기록부가 닫는다) 여기서 여는 시트는 PIN 을 다시 묻지 않는다.
 */
@Composable
fun ExportScreen(
    month: YearMonth,
    isWide: Boolean,
    onBack: () -> Unit,
    onOpenTrip: (Long) -> Unit,
    onFillVehicle: () -> Unit,
    onEditPerson: (Long) -> Unit,
    viewModel: ExportViewModel = viewModel(factory = ExportViewModel.Factory),
) {
    LaunchedEffect(month) { viewModel.show(month) }
    val exportState by viewModel.state.collectAsStateWithLifecycle()
    // 기록부에서 다른 달을 보고 왔으면 그 달로 바뀔 때까지 지난 상태는 그리지 않는다
    val state = exportState?.takeIf { it.monthExport.month == month } ?: return

    ScreenColumn(contentPadding = if (isWide) WideScreenPadding else ScreenPadding) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            HeaderButton(
                onClick = onBack,
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                text = stringResource(R.string.export_back),
            )
            Text(stringResource(R.string.export_title), fontSize = TextSizes.title, fontWeight = FontWeight.Bold)
        }
        SegmentedControl(
            options = ExportMode.entries,
            selected = state.mode,
            label = { stringResource(it.labelRes) },
            onSelect = viewModel::selectMode,
        )
        if (state.missingVehicleFields.isNotEmpty()) MissingVehicleNotice(state.missingVehicleFields, onFillVehicle)
        when (state.mode) {
            ExportMode.MONTH -> MonthExportBody(state.monthExport, onOpenTrip, onEditPerson)
            ExportMode.YEAR -> YearExportBody(state.yearExport, onOpenTrip)
        }
    }
}

// 월별·연간 모두 서식 머리를 쓰니 빠진 값 경고는 종류 고르기 아래에 한 번만 둔다
@Composable
private fun MissingVehicleNotice(fields: List<VehicleField>, onFill: () -> Unit) {
    val names = fields.map { stringResource(it.labelRes) }.joinToString(Formatters.SEPARATOR)
    FixWarning(
        text = stringResource(R.string.export_vehicle_missing, names),
        button = stringResource(R.string.export_fix_vehicle),
        onClick = onFill,
        icon = Icons.Rounded.Edit,
    )
}
