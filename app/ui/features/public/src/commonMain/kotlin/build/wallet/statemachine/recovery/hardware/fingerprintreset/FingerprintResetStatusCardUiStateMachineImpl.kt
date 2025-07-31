package build.wallet.statemachine.recovery.hardware.fingerprintreset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.privilegedactions.FingerprintResetService
import build.wallet.coroutines.flow.launchTicker
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logError
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.root.RemainingRecoveryDelayWordsUpdateFrequency
import com.github.michaelbull.result.onFailure
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlin.time.Duration

@BitkeyInject(ActivityScope::class)
class FingerprintResetStatusCardUiStateMachineImpl(
  private val clock: Clock,
  private val fingerprintResetService: FingerprintResetService,
  private val remainingRecoveryDelayWordsUpdateFrequency:
    RemainingRecoveryDelayWordsUpdateFrequency,
) : FingerprintResetStatusCardUiStateMachine {
  @Composable
  override fun model(props: FingerprintResetStatusCardUiProps): CardModel? {
    LaunchedEffect(props.account) {
      fingerprintResetService.getLatestFingerprintResetAction()
        .onFailure { error ->
          logError { "Failed to fetch fingerprint reset action for card: $error" }
        }
    }

    val pendingAction by fingerprintResetService.fingerprintResetAction().collectAsState()

    return pendingAction?.let { action ->
      val delayAndNotifyStrategy =
        action.authorizationStrategy as? AuthorizationStrategy.DelayAndNotify
          ?: return@let null // Should not happen for fingerprint reset

      val actualEndTime = delayAndNotifyStrategy.delayEndTime

      var remainingDuration by remember(actualEndTime) {
        mutableStateOf(actualEndTime - clock.now())
      }

      val remainingDelayInWords by remember(remainingDuration) {
        derivedStateOf {
          durationText(remainingDuration)
        }
      }

      LaunchedEffect(action.id, actualEndTime) {
        // 1) Periodically refresh the displayed remaining time
        launchTicker(remainingRecoveryDelayWordsUpdateFrequency.value) {
          val newRemaining = actualEndTime - clock.now()
          remainingDuration = if (newRemaining.isPositive()) newRemaining else Duration.ZERO
        }

        // 2) Also schedule a single wake-up exactly at the end of the delay and notify period so
        //    the card disappears promptly without polling more frequently than necessary.
        val delayUntilEnd = actualEndTime - clock.now()
        if (delayUntilEnd.isPositive()) {
          delay(delayUntilEnd)
          remainingDuration = Duration.ZERO
        }
      }

      // Don't show the card when the fingerprint reset is ready to be approved
      // It will be shown as a recommendation to complete instead
      if (!remainingDuration.isPositive()) {
        return@let null
      }

      FingerprintResetCardModel(
        title = "Fingerprint reset in progress",
        subtitle = remainingDelayInWords,
        onClick = { props.onClick(action.id) }
      )
    }
  }

  private fun durationText(remainingDuration: Duration): String {
    if (!remainingDuration.isPositive()) {
      return "Ready to complete"
    }

    return remainingDuration.toComponents { days, hours, minutes, _, _ ->
      when {
        days > 1 -> "$days days remaining..."
        days == 1L -> "1 day remaining..."
        hours > 1 -> "$hours hours remaining..."
        hours == 1 -> "1 hour remaining..."
        minutes > 1 -> "$minutes minutes remaining..."
        minutes == 1 -> "1 minute remaining..."
        else -> "Less than 1 minute remaining..."
      }
    }
  }
}
