package build.wallet.firmware

import okio.ByteString

data class FirmwareCoredump(
  val coredump: ByteString,
  val identifiers: TelemetryIdentifiers,
)
