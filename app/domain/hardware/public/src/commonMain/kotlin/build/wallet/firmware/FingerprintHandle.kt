package build.wallet.firmware

/**
 * An {index, label} tuple describing a fingerprint stored on the hardware.
 *
 * [label] will be an empty string ("") if not explicitly set.
 */
data class FingerprintHandle(
  val index: Int,
  val label: String,
) {
  /**
   * Returns [label] if non-empty, otherwise a default display name like "Fingerprint 1".
   */
  val displayLabel: String get() = label.ifEmpty { defaultLabel(index) }

  companion object {
    /**
     * Default display label for a fingerprint at the given [index] (0-based).
     * Produces "Fingerprint 1", "Fingerprint 2", etc.
     */
    fun defaultLabel(index: Int): String = "Fingerprint ${index + 1}"
  }
}
