package build.wallet.statemachine.recovery.hardware

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.Progress
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.RotatingAuthData.ReadyToCompleteRecoveryData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.WaitingForRecoveryDelayPeriodData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.LostHardwareRecoveryInProgressData
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.time.DurationFormatter
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

class HardwareRecoveryStatusCardUiStateMachineImpl(
  private val clock: Clock,
  private val durationFormatter: DurationFormatter,
) : HardwareRecoveryStatusCardUiStateMachine {
  @Composable
  override fun model(props: HardwareRecoveryStatusCardUiProps): CardModel? {
    return when (val lostHardwareRecoveryData = props.lostHardwareRecoveryData) {
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
              while (true) {
                remainingDelayPeriod = recoveryInProgressData.remainingDelayPeriod(clock)
                delay(DurationFormatter.MINIMUM_DURATION_WORD_FORMAT_UPDATE)
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
