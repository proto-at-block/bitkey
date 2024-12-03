package build.wallet.firmware

import build.wallet.logging.*
import build.wallet.queueprocessor.process
import build.wallet.toByteString
import build.wallet.toUByteList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okio.ByteString

class FirmwareTelemetryUploaderImpl(
  private val appCoroutineScope: CoroutineScope,
  private val firmwareCoredumpProcessor: FirmwareCoredumpEventProcessor,
  private val firmwareTelemetryProcessor: FirmwareTelemetryEventProcessor,
  private val teltra: Teltra,
) : FirmwareTelemetryUploader {
  private val oldCoredumpSize: Int = 4096
  private val coredumpSize: Int = 588

  override fun addEvents(
    events: ByteString,
    identifiers: TelemetryIdentifiers,
  ) {
    val eventsBytes = events.toByteArray()
    if (eventsBytes.isEmpty()) {
      return
    }

    appCoroutineScope.launch {
      val translated =
        teltra.translateBitlogs(eventsBytes.toUByteList(), identifiers)
      if (translated.isEmpty()) {
        logWarn { "Failed to translate bitlogs" }
        return@launch
      }

      for (event in translated) {
        firmwareTelemetryProcessor.process(
          FirmwareTelemetryEvent(
            serial = identifiers.serial,
            event = event.toByteString()
          )
        )
      }
    }
  }

  override fun addCoredump(
    coredump: ByteString,
    identifiers: TelemetryIdentifiers,
  ) {
    if (coredump.size != coredumpSize && coredump.size != oldCoredumpSize) {
      logWarn { "Rejecting invalid coredump (${coredump.size} bytes)" }
      return
    }
    appCoroutineScope.launch {
      firmwareCoredumpProcessor.process(FirmwareCoredump(coredump, identifiers))
    }
  }
}
