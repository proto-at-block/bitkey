package build.wallet.statemachine.core

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.ui.model.button.ButtonModel

/**
 * Model for the splash screen when the customer is in the locked state.
 * This screen is shown when the customer is locked out of the app due to failed biometric authentication.
 *
 * @param unlockButtonModel The model for the unlock button.
 * @param eventTrackerScreenInfo The screen info for tracking events.
 * @param key The UI key for the model.
 */
data class SplashLockModel(
  val unlockButtonModel: ButtonModel,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
  override val key: String = "SplashLockModel",
) : BodyModel()
