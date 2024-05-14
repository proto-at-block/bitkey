package build.wallet.firmware

enum class UnlockMethod {
  UNSPECIFIED,

  /**  Biometric unlock such as fingerprint */
  BIOMETRICS,

  /** Secret unlock such as PIN */
  UNLOCK_SECRET,
}

/**
 * The most recent hardware unlock information.
 *
 * [fingerprintIdx] is only set if [unlockMethod] is [UnlockMethod.BIOMETRICS].
 */
data class UnlockInfo(
  val unlockMethod: UnlockMethod,
  val fingerprintIdx: Int?,
)
