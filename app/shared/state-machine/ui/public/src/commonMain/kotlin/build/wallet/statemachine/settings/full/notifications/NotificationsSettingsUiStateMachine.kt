package build.wallet.statemachine.settings.full.notifications

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData

/**
 * State machine for the settings screen that displays the currently set
 * notification touchpoints for the account
 */
interface NotificationsSettingsUiStateMachine : StateMachine<NotificationsSettingsProps, ScreenModel>

data class NotificationsSettingsProps(
  val accountData: ActiveFullAccountLoadedData,
  val onBack: () -> Unit,
)
