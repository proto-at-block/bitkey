package build.wallet.bitkey.factor

/**
 * A physical entity in which the customer can use to hold their keys.
 */
enum class PhysicalFactor {
  /**
   * Corresponds to an app key that is held on the phone.
   */
  App,

  /**
   * Corresponds to a hardware key that is held on the Bitkey hardware device.
   *
   */
  Hardware,
}
