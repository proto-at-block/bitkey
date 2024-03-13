package build.wallet.statemachine.settings.full.notifications

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData

/**
 * State machine for the settings screen that displays the currently set
 * notification touchpoints for the account
 */
interface RecoveryChannelSettingsUiStateMachine : StateMachine<RecoveryChannelSettingsProps, ScreenModel>

data class RecoveryChannelSettingsProps(
  val accountData: ActiveFullAccountLoadedData,
  val onBack: () -> Unit,
)
