package build.wallet.statemachine.dev.wallet

import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine

/**
 * Shows debug options for current Bitcoin wallet.
 */
interface BitcoinWalletDebugUiStateMachine : StateMachine<BitcoinWalletDebugProps, BodyModel>

data class BitcoinWalletDebugProps(
  val onBack: () -> Unit,
)
