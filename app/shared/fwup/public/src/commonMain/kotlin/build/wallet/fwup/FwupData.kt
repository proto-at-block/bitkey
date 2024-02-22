package build.wallet.fwup

import okio.ByteString
import kotlin.math.ceil

data class FwupData(
  /** The version string for the FW, e.g. 1.7.1 */
  val version: String,
  val chunkSize: UInt,
  val signatureOffset: UInt,
  val appPropertiesOffset: UInt,
  val firmware: ByteString,
  val signature: ByteString,
  val fwupMode: FwupMode,
) {
  fun finalSequenceId(): UInt {
    if (chunkSize == 0U) return 0U
    return ceil((firmware.size.toUInt() / chunkSize).toDouble()).toUInt()
  }
}
