package build.wallet.statemachine.core.form

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearOutSlowInEasing

internal const val FORM_WAITING_REVEAL_DELAY_MILLIS = 3_000

internal fun formWaitingRevealDelayMillis(isHardwareFake: Boolean): Int =
  if (isHardwareFake) 0 else FORM_WAITING_REVEAL_DELAY_MILLIS

internal const val FORM_WAITING_REVEAL_DURATION_MILLIS = 320
internal val FormWaitingRevealEasing: Easing = LinearOutSlowInEasing
