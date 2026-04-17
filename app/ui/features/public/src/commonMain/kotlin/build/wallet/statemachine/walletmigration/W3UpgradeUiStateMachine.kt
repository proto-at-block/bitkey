package build.wallet.statemachine.walletmigration

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for the W3 hardware upgrade flow.
 *
 * Guides user through upgrading from a W1 hardware device to a W3 device,
 * including pairing the new hardware, creating new keysets, and sweeping funds.
 */
interface W3UpgradeUiStateMachine :
  StateMachine<W3UpgradeUiProps, ScreenModel>

/**
 * Props for W3UpgradeUiStateMachine.
 */
data class W3UpgradeUiProps(
  /**
   * The account to upgrade.
   */
  val account: FullAccount,
  /**
   * Called when upgrade completes successfully.
   */
  val onUpgradeComplete: (FullAccount) -> Unit,
  /**
   * Called when user cancels upgrade or it fails.
   */
  val onExit: () -> Unit,
)
