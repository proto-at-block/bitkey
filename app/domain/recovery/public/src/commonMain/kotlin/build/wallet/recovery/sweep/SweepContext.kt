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
   * Sweeping from inactive keysets that belong to one historical hardware device.
   *
   * Special handling: only keysets matching [hardwareFingerprint] are included. Matching keysets
   * can use hardware signing because the customer still has the historical device in hand.
   */
  data class InactiveHardware(val hardwareFingerprint: String) : SweepContext

  /**
   * Sweeping as part of private wallet migration.
   * Special handling: skips server signing, uses App + Hardware only.
   */
  data object PrivateWalletMigration : SweepContext

  /**
   * Sweeping as part of a W3 hardware upgrade.
   * Special handling: uses App + old Hardware to sign keysets that belonged to the
   * replaced device. Keysets from other historical devices stay on AppAndServer.
   *
   * @param replacedHardwareFingerprint Master key fingerprint of the old hardware device
   *   that was just replaced. Only keysets matching this fingerprint will use
   *   AppAndHardware signing; other inactive keysets fall back to AppAndServer.
   */
  data class W3Upgrade(val replacedHardwareFingerprint: String) : SweepContext

  /**
   * Sweeping as part of recovery after losing a factor.
   * @param recoveredFactor The factor that was recovered (App or Hardware).
   */
  data class Recovery(val recoveredFactor: PhysicalFactor) : SweepContext
}
