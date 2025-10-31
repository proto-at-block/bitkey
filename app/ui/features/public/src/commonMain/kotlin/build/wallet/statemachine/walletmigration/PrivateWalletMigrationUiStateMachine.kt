package build.wallet.statemachine.walletmigration

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for private wallet migration flow.
 *
 * Guides user through migrating from legacy multisig to private collaborative custody,
 * including keyset creation, fund sweep, and finalization.
 */
interface PrivateWalletMigrationUiStateMachine :
  StateMachine<PrivateWalletMigrationUiProps, ScreenModel>

/**
 * Props for PrivateWalletMigrationUiStateMachine.
 */
data class PrivateWalletMigrationUiProps(
  /**
   * The account to migrate to private wallet.
   */
  val account: FullAccount,
  /**
   * Called when migration completes successfully.
   * Returns the updated account with new private keyset.
   */
  val onMigrationComplete: (FullAccount) -> Unit,
  /**
   * Called when user cancels migration or it fails.
   */
  val onExit: () -> Unit,
  /**
   * Whether the migration flow was started while in-progress.
   */
  val inProgress: Boolean = false,
)
