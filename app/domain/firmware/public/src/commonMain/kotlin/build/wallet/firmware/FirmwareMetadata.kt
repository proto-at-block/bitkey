package build.wallet.firmware

import kotlinx.datetime.Instant
import okio.ByteString

data class FirmwareMetadata(
  val activeSlot: FirmwareSlot,
  val gitId: String,
  val gitBranch: String,
  val version: String,
  val build: String,
  val timestamp: Instant,
  val hash: ByteString,
  val hwRevision: String,
) {
  enum class FirmwareSlot {
    A,
    B,
  }

  public fun versionAsUInt(): UInt {
    return version.replace(".", "").toUInt()
  }
}

enum class SecureBootConfig {
  NOT_SET, // Old firmware versions don't the config.
  DEV,
  PROD,
}
