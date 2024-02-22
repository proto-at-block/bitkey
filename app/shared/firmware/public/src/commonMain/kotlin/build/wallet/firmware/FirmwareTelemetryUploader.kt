package build.wallet.firmware

import okio.ByteString

interface FirmwareTelemetryUploader {
  /** Save events to the database. */
  fun addEvents(
    events: ByteString,
    identifiers: TelemetryIdentifiers,
  )

  /** Save coredump to the database. */
  fun addCoredump(
    coredump: ByteString,
    identifiers: TelemetryIdentifiers,
  )
}
