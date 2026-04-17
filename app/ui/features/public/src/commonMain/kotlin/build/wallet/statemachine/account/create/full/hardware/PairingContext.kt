package build.wallet.statemachine.account.create.full.hardware

import bitkey.account.HardwareType

/**
 * Represents the different contexts in which hardware pairing can occur.
 */
sealed interface PairingContext {
  /**
   * Context for pairing hardware during lost hardware recovery.
   */
  data object LostHardware : PairingContext

  /**
   * Context for pairing hardware during initial wallet setup and onboarding.
   */
  data object Onboarding : PairingContext

  /**
   * Context for pairing a new W3 hardware device during W3 upgrade migration.
   * Forces W3 onboarding flow regardless of feature flags, and overrides
   * fake hardware to behave as W3.
   */
  data object W3Upgrade : PairingContext
}

/**
 * Returns the expected hardware type for this pairing context, or null if any type is acceptable.
 * Used to fail fast during pairing if the wrong device is tapped.
 */
fun PairingContext.expectedHardwareType(): HardwareType? =
  when (this) {
    is PairingContext.W3Upgrade -> HardwareType.W3
    else -> null
  }
