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
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import bitkey.privilegedactions.FingerprintResetService
import build.wallet.coroutines.flow.launchTicker
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logError
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.time.DurationFormatter
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

@BitkeyInject(ActivityScope::class)
class FingerprintResetStatusCardUiStateMachineImpl(
  private val clock: Clock,
  private val fingerprintResetService: FingerprintResetService,
) : FingerprintResetStatusCardUiStateMachine {
  private val pendingActionFlow = MutableStateFlow<PrivilegedActionInstance?>(null)

  @Composable
  override fun model(props: FingerprintResetStatusCardUiProps): CardModel? {
    LaunchedEffect(props.account) {
      fingerprintResetService.getLatestFingerprintResetAction()
        .onSuccess { action ->
          pendingActionFlow.update { action }
        }
        .onFailure { error ->
          logError { "Failed to fetch fingerprint reset action for card: $error" }
          pendingActionFlow.update { null }
        }
    }

    val pendingAction by pendingActionFlow.collectAsState()

    return pendingAction?.let { action ->
      val delayAndNotifyStrategy = action.authorizationStrategy as? AuthorizationStrategy.DelayAndNotify
        ?: return@let null // Should not happen for fingerprint reset

      val actualEndTime: Instant = delayAndNotifyStrategy.delayEndTime

      var remainingDuration by remember(actualEndTime) {
        mutableStateOf(actualEndTime - clock.now())
      }

      val remainingDelayInWords by remember(remainingDuration) {
        derivedStateOf {
          durationText(remainingDuration)
        }
      }

      LaunchedEffect(action.id, actualEndTime) {
        launchTicker(DurationFormatter.MINIMUM_DURATION_WORD_FORMAT_UPDATE) {
          val newRemaining = actualEndTime - clock.now()
          remainingDuration = if (newRemaining.isPositive()) newRemaining else Duration.ZERO
        }
      }

      FingerprintResetCardModel(
        title = "Fingerprint reset in progress",
        subtitle = remainingDelayInWords,
        onClick = { props.onClick(action.id) }
      )
    }
  }

  private fun durationText(remainingDuration: Duration): String =
    if (remainingDuration.isPositive()) {
      val days = remainingDuration.inWholeDays.toInt()
      when {
        days > 1 -> "$days days remaining..."
        days == 1 -> "1 day remaining..."
        else -> {
          val hours = remainingDuration.inWholeHours.toInt()
          when {
            hours > 1 -> "$hours hours remaining..."
            hours == 1 -> "1 hour remaining..."
            else -> "Less than 1 hour remaining..."
          }
        }
      }
    } else {
      "Ready to complete"
    }
}
