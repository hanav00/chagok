package kr.chagok.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.chagok.R
import kr.chagok.ui.components.ActionButton
import kr.chagok.ui.components.ButtonStyle
import kr.chagok.ui.components.SheetOverlay
import kr.chagok.ui.components.pressable
import kr.chagok.ui.theme.ChagokColors
import kr.chagok.ui.theme.Shapes
import kr.chagok.ui.theme.Spacing
import kr.chagok.ui.theme.TextSizes
import kr.chagok.ui.theme.WindowWidths

private val SIDE_PADDING = Spacing.xxl
private val SIDE_PADDING_WIDE = Spacing.xxxl
private val CONTENT_GAP = Spacing.m
private val CONTENT_GAP_WIDE = Spacing.xl
private val BAR_HEIGHT = 6.dp

/** 단계 막대의 묶음 이름. 어느 단계가 어느 묶음인지는 [barIndex] */
private val BAR_LABELS = listOf(
    R.string.onboarding_bar_start,
    R.string.onboarding_bar_car,
    R.string.onboarding_bar_mode,
    R.string.onboarding_bar_places,
    R.string.onboarding_bar_learn,
    R.string.onboarding_bar_done,
)

/**
 * 첫 실행 화면: 머리(제목 + 건너뛰기) · 단계 막대 · 단계 본문 · 아래 이전/다음.
 * 아래 버튼 줄은 내용이 길어도 늘 보이게 고정하고 본문만 넘긴다.
 * 관리자 PIN 은 껍데기가 띄우는 PIN 시트가 바로 저장하고, 정했는지는 저장소에서 다시 읽는다.
 */
@Composable
fun OnboardingScreen(
    onSetupPin: (onDone: () -> Unit) -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val labels = rememberUnnamedLabels()
    var isSkipAsked by rememberSaveable { mutableStateOf(false) }
    val howPlace = stringResource(R.string.onboarding_how_sample_place)
    LaunchedEffect(howPlace) { viewModel.setHowPlace(howPlace) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = maxWidth >= WindowWidths.wide
        val side = if (isWide) SIDE_PADDING_WIDE else SIDE_PADDING
        Column(Modifier.fillMaxSize()) {
            Head(
                state.step,
                onSkip = { isSkipAsked = true },
                Modifier.padding(start = side, end = side, top = Spacing.l),
            )
            if (state.step != OnboardingStep.WELCOME) {
                StepBar(state.step, Modifier.padding(start = side, end = side, top = Spacing.m))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = side, vertical = if (isWide) Spacing.xxl else Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(if (isWide) CONTENT_GAP_WIDE else CONTENT_GAP),
            ) {
                when (state.step) {
                    OnboardingStep.WELCOME -> WelcomeStep(isWide)
                    OnboardingStep.WHO -> WhoStep(state, isWide, viewModel::selectUsage)
                    OnboardingStep.PEOPLE -> PeopleStep(
                        state,
                        labels,
                        isWide,
                        viewModel::addPersonByVoice,
                        viewModel::addUnnamedPerson,
                    )
                    OnboardingStep.ADMIN -> AdminStep(state, isWide, onSetupPin = { onSetupPin {} })
                    OnboardingStep.MODE -> ModeStep(state, isWide, viewModel::selectDefaultCategory)
                    OnboardingStep.PLACES -> PlacesStep(state, isWide, viewModel::selectPlace)
                    OnboardingStep.HOW -> HowStep(state, isWide, viewModel::speak)
                    OnboardingStep.TRY -> TryStep(state, isWide, viewModel::tapPractice)
                    OnboardingStep.DONE -> DoneStep(state, labels, isWide)
                }
            }
            HorizontalDivider(color = ChagokColors.divider)
            Actions(
                state = state,
                onPrevious = { viewModel.previous(from = state.step) },
                onNext = { viewModel.next(from = state.step) },
                onFinish = { viewModel.finish(labels) },
                modifier = Modifier.padding(start = side, end = side, top = Spacing.m, bottom = Spacing.xl),
            )
        }
        if (isSkipAsked) {
            SheetOverlay(
                onDismiss = { isSkipAsked = false },
                header = {
                    Text(
                        stringResource(R.string.onboarding_skip_title),
                        fontSize = TextSizes.title,
                        fontWeight = FontWeight.Bold,
                    )
                },
            ) {
                SkipConfirmBody(
                    onContinue = { isSkipAsked = false },
                    onSkip = {
                        isSkipAsked = false
                        viewModel.skip()
                    },
                )
            }
        }
    }
}

// 이름 없이 등록한 사람 이름표 ("나", "이름 없음 3")
@Composable
private fun rememberUnnamedLabels(): UnnamedLabels {
    val context = LocalContext.current
    val me = stringResource(R.string.onboarding_me)
    return remember(context, me) {
        UnnamedLabels(me) { number -> context.getString(R.string.person_unnamed, number.toString()) }
    }
}

@Composable
private fun Head(step: OnboardingStep, onSkip: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.onboarding_title), fontSize = TextSizes.subtitle, fontWeight = FontWeight.Bold)
        if (step != OnboardingStep.DONE) SkipButton(onSkip)
    }
}

// 아래 버튼 줄: 왼쪽 "이전"(첫 단계에는 없다), 오른쪽 "다음"이나 정리 화면의 "시작하기"
@Composable
private fun Actions(
    state: OnboardingUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.previousStep != null) {
            OnboardingButton(
                text = stringResource(R.string.onboarding_prev),
                onClick = onPrevious,
                style = OnboardingButtonStyle.Plain,
                iconBefore = Icons.AutoMirrored.Rounded.ArrowBack,
            )
        }
        Spacer(Modifier.weight(1f))
        if (state.step == OnboardingStep.DONE) {
            OnboardingButton(stringResource(R.string.onboarding_finish), onClick = onFinish)
        } else {
            OnboardingButton(
                text = stringResource(nextLabel(state)),
                onClick = onNext,
                iconAfter = Icons.AutoMirrored.Rounded.ArrowForward,
                enabled = state.isNextEnabled,
            )
        }
    }
}

// 말해 보기를 아직 안 했으면 "다음" 대신 "나중에 해 볼게요". 안 한 연습은 홈 시작 가이드에 남는다
@StringRes
private fun nextLabel(state: OnboardingUiState): Int = when {
    state.step == OnboardingStep.WELCOME -> R.string.onboarding_welcome_start
    state.step == OnboardingStep.TRY && state.practice?.isDone != true -> R.string.onboarding_try_later
    else -> R.string.onboarding_next
}

private val OnboardingStep.barIndex: Int
    get() = when (this) {
        OnboardingStep.WELCOME -> 0
        OnboardingStep.WHO, OnboardingStep.PEOPLE, OnboardingStep.ADMIN -> 1
        OnboardingStep.MODE -> 2
        OnboardingStep.PLACES -> 3
        OnboardingStep.HOW, OnboardingStep.TRY -> 4
        OnboardingStep.DONE -> 5
    }

/** 이름이 붙은 단계 막대. 지금 묶음까지 강조색 */
@Composable
private fun StepBar(step: OnboardingStep, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        BAR_LABELS.forEachIndexed { index, label ->
            val isOn = index <= step.barIndex
            val isCurrent = index == step.barIndex
            val color by animateColorAsState(
                if (isOn) ChagokColors.accent else ChagokColors.cardStrong,
                label = "bar",
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Box(Modifier.fillMaxWidth().height(BAR_HEIGHT).background(color, Shapes.pill))
                Text(
                    stringResource(label),
                    color = if (isCurrent) ChagokColors.text else ChagokColors.textDim,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    fontSize = TextSizes.body,
                )
            }
        }
    }
}

@Composable
private fun SkipButton(onClick: () -> Unit) {
    Text(
        stringResource(R.string.onboarding_skip),
        modifier = Modifier.pressable(onClick = onClick).padding(horizontal = Spacing.m, vertical = Spacing.s),
        color = ChagokColors.textSub,
    )
}

/** 건너뛰기 확인. 기록은 이미 돌고 있고, 남은 건 홈 시작 가이드에서 이어서 한다 */
@Composable
private fun SkipConfirmBody(onContinue: () -> Unit, onSkip: () -> Unit) {
    Text(stringResource(R.string.onboarding_skip_body), color = ChagokColors.textSub)
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
    ) {
        ActionButton(stringResource(R.string.onboarding_skip_continue), onClick = onContinue)
        ActionButton(stringResource(R.string.onboarding_skip), onClick = onSkip, style = ButtonStyle.Primary)
    }
}
