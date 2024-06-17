package build.wallet.recovery.sweep

import kotlinx.coroutines.flow.Flow

/**
 * Determines if the user should be prompted to perform a sweep transaction
 * based on the state of the current wallet.
 */
interface SweepPromptRequirementCheck {
  /**
   * Whether the user should perform a sweep transaction.
   */
  val sweepRequired: Flow<Boolean>
}
