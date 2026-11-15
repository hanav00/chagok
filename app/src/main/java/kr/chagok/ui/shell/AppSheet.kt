package kr.chagok.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import kr.chagok.ui.admin.PinMode
import kr.chagok.ui.admin.PinSheet
import kr.chagok.ui.guide.HowSheet
import kr.chagok.ui.guide.TrySheet
import kr.chagok.ui.home.SummarySheet
import kr.chagok.ui.people.PersonSheet
import kr.chagok.ui.simulator.SimulatorDetailSheet
import kr.chagok.ui.table.TripSheet
import kr.chagok.ui.vehicle.VehicleInfoSheet
import kr.chagok.ui.vehicle.VehicleSheetScope

/**
 * 앱 창 위에 뜬 시트. 시트가 창 전체를 덮어 아래 화면을 누를 수 없으므로 한 번에 하나만 뜬다.
 */
internal sealed interface AppSheet {
    data class Trip(val tripId: Long) : AppSheet

    data class Vehicle(val scope: VehicleSheetScope) : AppSheet

    /** [target] 은 사람 id 또는 PersonSheetTarget.NEW */
    data class Person(val target: Long) : AppSheet

    /** 홈 "자세히": 올해·이번 달 분류별 거리 */
    data object Summary : AppSheet

    /** 절세 계산 "계산 자세히": 비교표와 가정 */
    data object SimulatorDetail : AppSheet

    /** 시작 가이드 "해 보기" */
    data object Try : AppSheet

    /** 설정 "튜토리얼 다시 보기" */
    data object How : AppSheet

    /** 관리자 PIN. 끝나면(PIN 이 맞거나 새로 정함) [onDone] */
    data class Pin(val mode: PinMode, val onDone: () -> Unit) : AppSheet

    companion object {
        private const val TRIP = "trip"
        private const val VEHICLE = "vehicle"
        private const val PERSON = "person"
        private const val TRY = "try"
        private const val HOW = "how"
        private const val SUMMARY = "summary"
        private const val SIMULATOR_DETAIL = "simulator_detail"

        /** PIN 시트는 끝난 뒤 할 일(람다)을 저장할 수 없어서 화면을 다시 만들면 닫힌다 */
        val Saver: Saver<AppSheet?, Any> = listSaver(
            save = { sheet ->
                when (sheet) {
                    is Trip -> listOf(TRIP, sheet.tripId)
                    is Vehicle -> listOf(VEHICLE, sheet.scope)
                    is Person -> listOf(PERSON, sheet.target)
                    Try -> listOf(TRY)
                    How -> listOf(HOW)
                    Summary -> listOf(SUMMARY)
                    SimulatorDetail -> listOf(SIMULATOR_DETAIL)
                    is Pin, null -> emptyList()
                }
            },
            restore = { saved ->
                when (saved.first()) {
                    TRIP -> Trip(saved[1] as Long)
                    VEHICLE -> Vehicle(saved[1] as VehicleSheetScope)
                    PERSON -> Person(saved[1] as Long)
                    TRY -> Try
                    HOW -> How
                    SUMMARY -> Summary
                    SIMULATOR_DETAIL -> SimulatorDetail
                    else -> null
                }
            },
        )
    }
}

/** @param onOpen 이 시트를 닫고 다른 시트로 넘어가기 (튜토리얼 다시 보기 → 말해 보기) */
@Composable
internal fun AppSheetLayer(sheet: AppSheet, isWide: Boolean, onClose: () -> Unit, onOpen: (AppSheet) -> Unit) {
    when (sheet) {
        is AppSheet.Trip -> TripSheet(tripId = sheet.tripId, onDismiss = onClose)
        is AppSheet.Vehicle -> VehicleInfoSheet(scope = sheet.scope, onDismiss = onClose)
        is AppSheet.Person -> PersonSheet(target = sheet.target, onDismiss = onClose)
        AppSheet.Try -> TrySheet(isWide = isWide, onDismiss = onClose)
        AppSheet.How -> HowSheet(isWide = isWide, onDismiss = onClose, onTry = { onOpen(AppSheet.Try) })
        AppSheet.Summary -> SummarySheet(onDismiss = onClose)
        AppSheet.SimulatorDetail -> SimulatorDetailSheet(onDismiss = onClose)
        is AppSheet.Pin -> PinSheet(
            mode = sheet.mode,
            onDismiss = onClose,
            onDone = {
                onClose()
                sheet.onDone()
            },
        )
    }
}
