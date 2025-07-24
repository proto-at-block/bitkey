package build.wallet.statemachine.account.create.full.hardware

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
}
