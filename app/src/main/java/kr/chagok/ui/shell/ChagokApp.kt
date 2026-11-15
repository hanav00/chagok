package kr.chagok.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.chagok.ui.admin.PinMode
import kr.chagok.ui.components.NoticeBar
import kr.chagok.ui.components.NoticeBarState
import kr.chagok.ui.guide.GuideItem
import kr.chagok.ui.home.HomeScreen
import kr.chagok.ui.onboarding.OnboardingScreen
import kr.chagok.ui.settings.SettingsScreen
import kr.chagok.ui.simulator.SimulatorScreen
import kr.chagok.ui.table.TableScreen
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.WindowWidths
import kr.chagok.ui.vehicle.VehicleSheetScope

/**
 * 차곡 앱 껍데기: 헤더 + 탭 + 화면, 그 위에 뜨는 시트. 넓은 창은 탭이 헤더 안에 들어간다.
 * P단이 아니면 탭과 화면 대신 이번 운행 분류 한 줄만 보이고, 열려 있던 시트도 닫힌다.
 * 첫 실행이 끝나기 전에는 탭 대신 첫 실행 화면이 뜨고, 기록은 그동안에도 계속된다.
 */
@Composable
fun ChagokApp(viewModel: ShellViewModel = viewModel(factory = ShellViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var sheet by rememberSaveable(stateSaver = AppSheet.Saver) { mutableStateOf<AppSheet?>(null) }
    val notices = remember { NoticeBarState() }

    LaunchedEffect(Unit) {
        viewModel.recentTripShown.collect {
            tab = Tab.HOME
            if (sheet is AppSheet.Trip) sheet = null
        }
    }
    // 알림은 차례로 하나씩. 주행 중에는 꺼내지 않고(보이던 것도 내린다) 주차하면 쌓인 것부터 보인다
    val isParked = state?.isDriving == false
    LaunchedEffect(isParked) {
        if (isParked) viewModel.messages.collect { notices.show(it) }
    }
    // 출발하면 PIN·차량·사람 시트 요청도 버린다. 남겨 두면 주차한 뒤 처음 단계부터 다시 뜬다.
    // 운행 상세는 남겨 두고 주차하면 다시 보인다
    LaunchedEffect(state?.isDriving) {
        if (state?.isDriving == true && sheet !is AppSheet.Trip) sheet = null
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(ChagokColors.window)) {
        val shell = state ?: return@BoxWithConstraints // 설정을 읽는 동안은 바탕만
        val isWide = maxWidth >= WindowWidths.wide

        Column(Modifier.fillMaxSize()) {
            val hasTabs = !shell.isDriving && shell.isFirstRunDone
            AppHeader(
                isDriving = shell.isDriving,
                isAdminUnlocked = shell.isAdminUnlocked,
                onLockAdmin = viewModel::lockAdmin,
                tabs = if (hasTabs && isWide) {
                    { HeaderTabs(selected = tab, homeBadge = shell.badgeCount, onSelect = { tab = it }) }
                } else {
                    null
                },
            )
            when {
                shell.isDriving -> DrivingScreen(shell.drivingCategory)
                !shell.isFirstRunDone -> OnboardingScreen(
                    onSetupPin = { onDone -> sheet = AppSheet.Pin(PinMode.SETUP, onDone) },
                )
                else -> {
                    if (!isWide) TabBar(selected = tab, homeBadge = shell.badgeCount, onSelect = { tab = it })
                    TabContent(
                        tab = tab,
                        isWide = isWide,
                        onSelectTab = { tab = it },
                        onOpenSheet = { sheet = it },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }

        if (!shell.isDriving) {
            sheet?.let { AppSheetLayer(it, isWide, onClose = { sheet = null }, onOpen = { next -> sheet = next }) }
            // 시트 위에도 보이게 맨 마지막에
            NoticeBar(notices, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun TabContent(
    tab: Tab,
    isWide: Boolean,
    onSelectTab: (Tab) -> Unit,
    onOpenSheet: (AppSheet) -> Unit,
    modifier: Modifier,
) {
    val openPin = { mode: PinMode, onDone: () -> Unit -> onOpenSheet(AppSheet.Pin(mode, onDone)) }
    val editPerson = { target: Long -> onOpenSheet(AppSheet.Person(target)) }
    Box(modifier) {
        when (tab) {
            Tab.HOME -> HomeScreen(
                isWide = isWide,
                onOpenDetail = { onOpenSheet(AppSheet.Summary) },
                onGuideAction = { item ->
                    when (item) {
                        GuideItem.ADMIN_PIN -> openPin(PinMode.SETUP) {}
                        GuideItem.PLACES -> onSelectTab(Tab.SETTINGS)
                        GuideItem.CHANGE -> onOpenSheet(AppSheet.Try)
                        GuideItem.RECORDING, GuideItem.FIRST_TRIP -> Unit
                    }
                },
            )
            Tab.TABLE -> TableScreen(
                isWide = isWide,
                onOpenTrip = { onOpenSheet(AppSheet.Trip(it)) },
                onOpenPin = openPin,
                onFillVehicle = { onOpenSheet(AppSheet.Vehicle(VehicleSheetScope.MISSING)) },
                onEditPerson = editPerson,
            )
            Tab.SIMULATOR -> SimulatorScreen(
                isWide = isWide,
                onOpenDetail = { onOpenSheet(AppSheet.SimulatorDetail) },
            )
            Tab.SETTINGS -> SettingsScreen(
                isWide = isWide,
                onOpenPin = openPin,
                onEditVehicle = { onOpenSheet(AppSheet.Vehicle(VehicleSheetScope.ALL)) },
                onEditPerson = editPerson,
                onOpenTutorial = { onOpenSheet(AppSheet.How) },
            )
        }
    }
}
