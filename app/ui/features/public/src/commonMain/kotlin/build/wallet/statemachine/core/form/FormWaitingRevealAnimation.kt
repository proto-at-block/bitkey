package build.wallet.statemachine.core.form

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearOutSlowInEasing

internal const val FORM_DS_V2_WAITING_REVEAL_DELAY_MILLIS = 3_000

internal fun formDsV2WaitingRevealDelayMillis(isHardwareFake: Boolean): Int =
  if (isHardwareFake) 0 else FORM_DS_V2_WAITING_REVEAL_DELAY_MILLIS

internal const val FORM_DS_V2_WAITING_REVEAL_DURATION_MILLIS = 320
internal val FormDsV2WaitingRevealEasing: Easing = LinearOutSlowInEasing
