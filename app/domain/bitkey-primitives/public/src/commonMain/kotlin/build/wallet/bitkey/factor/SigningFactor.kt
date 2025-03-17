package build.wallet.bitkey.factor

/**
 * A signing component that app can use to sign.
 */
enum class SigningFactor {
  /**
   * Corresponds to Bitkey hardware signing component.
   */
  Hardware,

  /**
   * Corresponds to server signing component.
   */
  F8e,
}
