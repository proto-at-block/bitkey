package build.wallet.statemachine.settings.full.notifications

import build.wallet.bitkey.account.Account
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for the settings screen that displays the currently set
 * notification touchpoints for the account
 */
interface RecoveryChannelSettingsUiStateMachine : StateMachine<RecoveryChannelSettingsProps, ScreenModel>

data class RecoveryChannelSettingsProps(
  val account: Account,
  val onBack: () -> Unit,
  val source: Source = Source.Settings,
  val onContinue: (() -> Unit)? = null,
)
