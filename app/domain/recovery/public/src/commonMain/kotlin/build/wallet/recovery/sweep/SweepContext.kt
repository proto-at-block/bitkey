package build.wallet.recovery.sweep

import build.wallet.bitkey.factor.PhysicalFactor

/**
 * Context for why a sweep is being performed.
 * Used throughout the sweep flow to customize behavior based on the sweep scenario.
 */
sealed interface SweepContext {
  /**
   * Sweeping from an inactive wallet to the active wallet.
   * This is the default behavior.
   */
  data object InactiveWallet : SweepContext

  /**
   * Sweeping as part of private wallet migration.
   * Special handling: skips server signing, uses App + Hardware only.
   */
  data object PrivateWalletMigration : SweepContext

  /**
   * Sweeping as part of recovery after losing a factor.
   * @param recoveredFactor The factor that was recovered (App or Hardware).
   */
  data class Recovery(val recoveredFactor: PhysicalFactor) : SweepContext
}
