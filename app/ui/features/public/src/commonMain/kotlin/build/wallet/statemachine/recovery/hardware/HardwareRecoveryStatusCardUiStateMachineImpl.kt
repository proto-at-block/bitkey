package build.wallet.statemachine.recovery.hardware

import androidx.compose.runtime.*
import bitkey.recovery.RecoveryStatusService
import build.wallet.Progress
import build.wallet.coroutines.flow.launchTicker
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.recovery.Recovery.StillRecovering
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.RotatingAuthData.ReadyToCompleteRecoveryData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.WaitingForRecoveryDelayPeriodData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.LostHardwareRecoveryInProgressData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryDataStateMachine
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryProps
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.time.DurationFormatter
import com.github.michaelbull.result.get
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

@BitkeyInject(ActivityScope::class)
class HardwareRecoveryStatusCardUiStateMachineImpl(
  private val clock: Clock,
  private val durationFormatter: DurationFormatter,
  private val lostHardwareRecoveryDataStateMachine: LostHardwareRecoveryDataStateMachine,
  private val recoveryStatusService: RecoveryStatusService,
) : HardwareRecoveryStatusCardUiStateMachine {
  @Composable
  override fun model(props: HardwareRecoveryStatusCardUiProps): CardModel? {
    val recovery by remember {
      recoveryStatusService.status()
        .map { it.get() }
        .filterIsInstance<StillRecovering?>()
    }.collectAsState(null)

    val lostHardwareRecoveryData = lostHardwareRecoveryDataStateMachine.model(
      props = LostHardwareRecoveryProps(
        account = props.account,
        hardwareRecovery = recovery
      )
    )

    return when (lostHardwareRecoveryData) {
      is LostHardwareRecoveryInProgressData ->
        when (val recoveryInProgressData = lostHardwareRecoveryData.recoveryInProgressData) {
          is ReadyToCompleteRecoveryData ->
            HardwareRecoveryCardModel(
              title = "Replacement Ready",
              delayPeriodProgress = Progress.Full,
              delayPeriodRemainingSeconds = 0,
              onClick = props.onClick
            )

          is WaitingForRecoveryDelayPeriodData -> {
            var remainingDelayPeriod by remember {
              mutableStateOf(recoveryInProgressData.remainingDelayPeriod(clock))
            }
            // Derive formatted delay period when the duration state is updated.
            val remainingDelayInWords by remember(remainingDelayPeriod) {
              derivedStateOf {
                durationFormatter.formatWithWords(remainingDelayPeriod)
              }
            }

            // Periodically update [remainingDelayPeriod] so that the formatted words update accordingly
            LaunchedEffect("update-delay-progress") {
              launchTicker(DurationFormatter.MINIMUM_DURATION_WORD_FORMAT_UPDATE) {
                remainingDelayPeriod = recoveryInProgressData.remainingDelayPeriod(clock)
              }
            }

            HardwareRecoveryCardModel(
              title = "Replacement pending...",
              subtitle = remainingDelayInWords,
              delayPeriodProgress = recoveryInProgressData.delayPeriodProgress(clock),
              delayPeriodRemainingSeconds = remainingDelayPeriod.inWholeSeconds,
              onClick = props.onClick
            )
          }

          else -> null
        }

      else -> null
    }
  }
}
