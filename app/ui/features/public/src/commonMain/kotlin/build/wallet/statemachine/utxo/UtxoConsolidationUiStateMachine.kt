package build.wallet.statemachine.utxo

import build.wallet.bitcoin.utxo.UtxoConsolidationContext
import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * UI flow that allows a customer to consolidate their UTXOs.
 */
interface UtxoConsolidationUiStateMachine : StateMachine<UtxoConsolidationProps, ScreenModel>

data class UtxoConsolidationProps(
  val account: FullAccount,
  val onConsolidationSuccess: () -> Unit,
  val onBack: () -> Unit,
  val context: UtxoConsolidationContext = UtxoConsolidationContext.Standard,
)
