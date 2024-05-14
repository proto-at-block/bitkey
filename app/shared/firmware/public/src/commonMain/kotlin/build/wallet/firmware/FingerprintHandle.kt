package build.wallet.firmware

/**
 * An {index, label} tuple describing a fingerprint stored on the hardware.
 *
 * [label] will be an empty string ("") if not explicitly set.
 */
data class FingerprintHandle(
  val index: Int,
  val label: String,
)
