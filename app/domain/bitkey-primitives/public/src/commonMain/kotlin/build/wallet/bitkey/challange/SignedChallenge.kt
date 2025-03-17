package build.wallet.bitkey.challange

import build.wallet.bitkey.factor.PhysicalFactor

/**
 * A [DelayNotifyChallenge] that has been signed by a local key.
 */
sealed interface SignedChallenge {
  /**
   * Original challenge data that was signed.
   */
  val challenge: DelayNotifyChallenge

  /**
   * The signature output when signing the [challenge]
   */
  val signature: String

  /**
   * The physical factor type that signed the challenge.
   */
  val signingFactor: PhysicalFactor

  /**
   * A [SignedChallenge] that has been signed by the app auth key..
   */
  data class AppSignedChallenge(
    override val challenge: DelayNotifyChallenge,
    override val signature: String,
  ) : SignedChallenge {
    override val signingFactor: PhysicalFactor = PhysicalFactor.App
  }

  /**
   * A [SignedChallenge] that has been signed by the hardware auth key.
   */
  data class HardwareSignedChallenge(
    override val challenge: DelayNotifyChallenge,
    override val signature: String,
  ) : SignedChallenge {
    override val signingFactor: PhysicalFactor = PhysicalFactor.Hardware
  }
}
