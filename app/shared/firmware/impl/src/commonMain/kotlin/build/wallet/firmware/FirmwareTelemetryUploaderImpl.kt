package build.wallet.firmware

import build.wallet.coroutines.scopes.CoroutineScopes
import build.wallet.logging.log
import build.wallet.queueprocessor.Processor
import build.wallet.toByteString
import build.wallet.toUByteList
import kotlinx.coroutines.launch
import okio.ByteString

class FirmwareTelemetryUploaderImpl(
  private val firmwareCoredumpProcessor: Processor<FirmwareCoredump>,
  private val firmwareTelemetryProcessor: Processor<FirmwareTelemetryEvent>,
  private val teltra: Teltra,
) : FirmwareTelemetryUploader {
  private val oldCoredumpSize: Int = 4096
  private val coredumpSize: Int = 588

  @OptIn(ExperimentalUnsignedTypes::class)
  override fun addEvents(
    events: ByteString,
    identifiers: TelemetryIdentifiers,
  ) {
    val eventsBytes = events.toByteArray()
    if (eventsBytes.isEmpty()) {
      return
    }

    CoroutineScopes.AppScope.launch {
      val translated =
        teltra.translateBitlogs(eventsBytes.toUByteList(), identifiers)
      if (translated.isEmpty()) {
        log { "Failed to translate bitlogs" }
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
      log { "Rejecting invalid coredump (${coredump.size} bytes)" }
      return
    }
    CoroutineScopes.AppScope.launch {
      firmwareCoredumpProcessor.process(FirmwareCoredump(coredump, identifiers))
    }
  }
}
