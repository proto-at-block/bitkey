package build.wallet.statemachine.core.form

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private const val HARDWARE_INTERACTION_REVEAL_DELAY_MILLIS = 3_000L

internal const val FORM_WAITING_REVEAL_DURATION_MILLIS = 320
internal val FormWaitingRevealEasing: Easing = LinearOutSlowInEasing

/**
 * Returns `false` while a brief hardware-interaction window is in progress, then `true` once the
 * window elapses. State machines should use this to defer revealing UI affordances (e.g. footer
 * buttons) on screens that wait for a fingerprint or NFC tap to complete.
 *
 * When [isHardwareFake] is true (developer/test builds without real hardware), the delay is
 * skipped and the call returns `true` immediately.
 */
@Composable
fun rememberHardwareInteractionRevealed(isHardwareFake: Boolean): Boolean {
  var revealed by remember(isHardwareFake) { mutableStateOf(isHardwareFake) }
  LaunchedEffect(isHardwareFake) {
    if (!isHardwareFake) {
      delay(HARDWARE_INTERACTION_REVEAL_DELAY_MILLIS.milliseconds)
      revealed = true
    }
  }
  return revealed
}
