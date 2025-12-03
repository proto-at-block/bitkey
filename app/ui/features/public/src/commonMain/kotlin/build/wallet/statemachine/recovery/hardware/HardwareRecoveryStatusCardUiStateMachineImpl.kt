package build.wallet.statemachine.recovery.hardware

import androidx.compose.runtime.*
import bitkey.recovery.RecoveryStatusService
import build.wallet.Progress
import build.wallet.coroutines.flow.launchTicker
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.recovery.ServerRecovery
import build.wallet.recovery.Recovery.StillRecovering.ServerDependentRecovery.InitiatedRecovery
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.root.RemainingRecoveryDelayWordsUpdateFrequency
import build.wallet.time.DurationFormatter
import build.wallet.time.durationProgress
import build.wallet.time.nonNegativeDurationBetween
import com.github.michaelbull.result.getOrElse
import kotlinx.datetime.Clock
import kotlin.time.Duration

@BitkeyInject(ActivityScope::class)
class HardwareRecoveryStatusCardUiStateMachineImpl(
  private val clock: Clock,
  private val durationFormatter: DurationFormatter,
  private val recoveryStatusService: RecoveryStatusService,
  private val remainingRecoveryDelayWordsUpdateFrequency:
    RemainingRecoveryDelayWordsUpdateFrequency,
) : HardwareRecoveryStatusCardUiStateMachine {
  @Composable
  override fun model(props: HardwareRecoveryStatusCardUiProps): CardModel? {
    val recovery by remember {
      recoveryStatusService.status
    }.collectAsState()

    return when (recovery) {
      is InitiatedRecovery -> {
        val initiatedRecovery = recovery as InitiatedRecovery
        val remainingDelayPeriod = initiatedRecovery.serverRecovery.remainingDelayPeriod(clock)

        when {
          remainingDelayPeriod == Duration.ZERO -> {
            // Delay period is complete, ready to complete recovery
            HardwareRecoveryCardModel(
              title = "Replacement Ready",
              delayPeriodProgress = Progress.Full,
              delayPeriodRemainingSeconds = 0,
              onClick = props.onClick
            )
          }
          else -> {
            // Still waiting for delay period
            var remainingDelay by remember {
              mutableStateOf(remainingDelayPeriod)
            }

            // Derive formatted delay period when the duration state is updated.
            val remainingDelayInWords by remember(remainingDelay) {
              derivedStateOf {
                durationFormatter.formatWithWords(remainingDelay)
              }
            }

            // Periodically update [remainingDelay] so that the formatted words update accordingly
            LaunchedEffect("update-delay-progress") {
              launchTicker(remainingRecoveryDelayWordsUpdateFrequency.value) {
                remainingDelay = initiatedRecovery.serverRecovery.remainingDelayPeriod(clock)
              }
            }

            val delayPeriodProgress = durationProgress(
              now = clock.now(),
              startTime = initiatedRecovery.serverRecovery.delayStartTime,
              endTime = initiatedRecovery.serverRecovery.delayEndTime
            ).getOrElse { Progress.Zero }

            HardwareRecoveryCardModel(
              title = "Replacement pending...",
              subtitle = remainingDelayInWords,
              delayPeriodProgress = delayPeriodProgress,
              delayPeriodRemainingSeconds = remainingDelay.inWholeSeconds,
              onClick = props.onClick
            )
          }
        }
      }

      else -> null
    }
  }

  private fun ServerRecovery.remainingDelayPeriod(clock: Clock): Duration =
    nonNegativeDurationBetween(
      startTime = clock.now(),
      endTime = delayEndTime
    )
}
