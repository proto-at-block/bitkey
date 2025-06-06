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
) {
  companion object {
    /**
     * Default unlock info for onboarding.
     * This is used to indicate that the user has not set up any additional unlock methods yet.
     */
    val ONBOARDING_DEFAULT = listOf(
      UnlockInfo(
        unlockMethod = UnlockMethod.BIOMETRICS,
        fingerprintIdx = 0
      )
    )
  }
}
