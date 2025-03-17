package build.wallet.firmware

import okio.ByteString

data class FirmwareTelemetryEvent(
  val serial: String,
  val event: ByteString,
)
