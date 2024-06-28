package build.wallet.statemachine.transactions

import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.SpeedUpTransactionDetails
import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * Guides the flow of updating the fee of a transaction that has been broadcasted.
 */
interface FeeBumpConfirmationUiStateMachine : StateMachine<FeeBumpConfirmationProps, ScreenModel>

/**
 * @property account The account that the transaction is associated with.
 * @property speedUpTransactionDetails The details of the transaction that is being sped up. See [SpeedUpTransactionDetails].
 * @property onExit Callback to exit the fee bump confirmation screen.
 * @property syncTransactions Callback to sync transactions once broadcasted again
 */
data class FeeBumpConfirmationProps(
  val account: FullAccount,
  val speedUpTransactionDetails: SpeedUpTransactionDetails,
  val psbt: Psbt,
  val newFeeRate: FeeRate,
  val onExit: () -> Unit,
  val syncTransactions: suspend () -> Unit,
)
