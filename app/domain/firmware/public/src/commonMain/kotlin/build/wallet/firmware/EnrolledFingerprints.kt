package build.wallet.firmware

/**
 * The set of [fingerprintHandles] enrolled on the device and the [maxCount] that are
 * supported by the version of firmware.
 */
data class EnrolledFingerprints(
  val maxCount: Int,
  val fingerprintHandles: List<FingerprintHandle>,
) {
  init {
    require(fingerprintHandles.size <= maxCount) { "Fingerprint handles exceeded max count $maxCount" }
  }
}
