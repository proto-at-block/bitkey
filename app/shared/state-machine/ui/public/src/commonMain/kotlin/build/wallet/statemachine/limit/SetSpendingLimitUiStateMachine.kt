package build.wallet.statemachine.limit

import build.wallet.bitkey.keybox.Keybox
import build.wallet.limit.SpendingLimit
import build.wallet.money.FiatMoney
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData

/**
 * State machine for entering and coordinating the Spending Limit (Mobile Pay) flow for setting the
 * desired limit
 */
interface SetSpendingLimitUiStateMachine : StateMachine<SpendingLimitProps, ScreenModel>

/**
 * @property currentSpendingLimit The existing spending limit the user has set, null if one has not
 *                             been set prior
 * @property onClose Action to be taken when the user closes out of the flow without setting a limit
 * @property onSetLimit Action to be taken when the user closes out of the flow after setting a limit
 * @property Keybox Reference to the keybox used for verifying with the hardware wallet
 */
data class SpendingLimitProps(
  val currentSpendingLimit: FiatMoney?,
  val onClose: () -> Unit,
  val onSetLimit: (SpendingLimit) -> Unit,
  val accountData: ActiveFullAccountLoadedData,
)

/**
 * Entry point for the setting up Spending Limit
 */
enum class SpendingLimitEntryPoint {
  Settings,
  GettingStarted,
}
