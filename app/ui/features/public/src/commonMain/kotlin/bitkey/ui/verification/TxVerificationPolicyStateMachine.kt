package bitkey.ui.verification

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * Screen for managing the current Transaction Verification Policy status.
 */
interface TxVerificationPolicyStateMachine : StateMachine<TxVerificationPolicyProps, ScreenModel>

data class TxVerificationPolicyProps(
  val onExit: () -> Unit,
  val account: FullAccount,
)
