package build.wallet.statemachine.account.create.full.onboard.notifications

import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.notifications.NotificationPreferencesProps

/**
 * Onboarding flow for setting up and requesting notification preferences.
 */
interface NotificationPreferencesSetupUiStateMachine : StateMachine<NotificationPreferencesSetupUiProps, ScreenModel>

/**
 * @property onComplete - callback for when all channels have either been opted in to or skipped.
 */
data class NotificationPreferencesSetupUiProps(
  val fullAccountId: FullAccountId,
  val fullAccountConfig: FullAccountConfig,
  val source: NotificationPreferencesProps.Source,
  val onComplete: () -> Unit,
)
