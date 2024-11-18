package build.wallet.statemachine.limit.picker

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.keybox.Keybox
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * Spending limit picker state machine
 *
 * @constructor Create empty Spending limit picker state machine
 */
interface SpendingLimitPickerUiStateMachine : StateMachine<SpendingLimitPickerUiProps, ScreenModel>

/**
 * @property accountData Reference to the [Keybox]
 * @property initialLimit The existing spending limit the user has set, null if one has not
 *                     been set prior
 * @property onRetreat Action to be taken when the user backs out of the flow
 * @property onSaveLimit Action to be called when the user commits to saving a limit and confirms
 * it with hardware.
 */
data class SpendingLimitPickerUiProps(
  val account: FullAccount,
  val initialLimit: FiatMoney,
  val retreat: Retreat,
  val onSaveLimit: (
    fiatLimit: FiatMoney,
    btcLimit: BitcoinMoney,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ) -> Unit,
)
