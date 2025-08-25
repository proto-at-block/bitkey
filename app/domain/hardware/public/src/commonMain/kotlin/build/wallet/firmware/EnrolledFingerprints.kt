package build.wallet.firmware

/**
 * The set of [fingerprintHandles] enrolled on the device and the [maxCount] that are
 * supported by the version of firmware.
 */
data class EnrolledFingerprints(
  val maxCount: Int = MAX_FINGERPRINT_COUNT,
  val fingerprintHandles: List<FingerprintHandle>,
) {
  init {
    require(fingerprintHandles.size <= maxCount) { "Fingerprint handles exceeded max count $maxCount" }
  }

  /**
   * Converts fingerprint handles to a list of UnlockInfo objects
   */
  fun toUnlockInfoList(): List<UnlockInfo> {
    return fingerprintHandles.map { fingerprintHandle ->
      UnlockInfo(
        unlockMethod = UnlockMethod.BIOMETRICS,
        fingerprintIdx = fingerprintHandle.index
      )
    }
  }

  companion object {
    /**
     * Maximum number of fingerprints that can be enrolled on the hardware device.
     */
    const val MAX_FINGERPRINT_COUNT = 3

    /**
     * Default index for the first fingerprint.
     */
    const val FIRST_FINGERPRINT_INDEX = 0
  }
}
