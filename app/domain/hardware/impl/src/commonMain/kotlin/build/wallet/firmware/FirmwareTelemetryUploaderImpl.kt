package build.wallet.firmware

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.*
import build.wallet.queueprocessor.process
import build.wallet.toByteString
import build.wallet.toUByteList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okio.ByteString

@BitkeyInject(AppScope::class)
class FirmwareTelemetryUploaderImpl(
  private val appCoroutineScope: CoroutineScope,
  private val firmwareCoredumpProcessor: FirmwareCoredumpEventProcessor,
  private val firmwareTelemetryProcessor: FirmwareTelemetryEventProcessor,
  private val teltra: Teltra,
) : FirmwareTelemetryUploader {
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
    val validCoredump = coredump.validMemfaultCoredump() ?: run {
      logWarn { "Rejecting invalid coredump (${coredump.size} bytes)" }
      return
    }

    appCoroutineScope.launch {
      firmwareCoredumpProcessor.process(FirmwareCoredump(validCoredump, identifiers))
    }
  }
}

private fun ByteString.validMemfaultCoredump(): ByteString? {
  if (size.toLong() < MEMFAULT_COREDUMP_HEADER_SIZE) return null

  val magic = readUInt32Le(offset = 0)
  val version = readUInt32Le(offset = 4)
  val totalSize = readUInt32Le(offset = 8)

  if (magic != MEMFAULT_COREDUMP_MAGIC) return null
  if (version < MIN_MEMFAULT_COREDUMP_VERSION_WITH_FOOTER) return null
  if (totalSize < MIN_MEMFAULT_COREDUMP_SIZE) return null
  if (totalSize > MAX_SUPPORTED_COREDUMP_SIZE) return null
  if (totalSize > size.toLong()) return null

  val footerMagic = readUInt32Le(offset = totalSize.toInt() - MEMFAULT_COREDUMP_FOOTER_SIZE)
  if (footerMagic != MEMFAULT_COREDUMP_FOOTER_MAGIC) return null

  return substring(0, totalSize.toInt())
}

private fun ByteString.readUInt32Le(offset: Int): Long =
  (this[offset].toLong() and BYTE_MASK) or
    ((this[offset + 1].toLong() and BYTE_MASK) shl 8) or
    ((this[offset + 2].toLong() and BYTE_MASK) shl 16) or
    ((this[offset + 3].toLong() and BYTE_MASK) shl 24)

private const val MEMFAULT_COREDUMP_MAGIC: Long = 0x45524f43L
private const val MEMFAULT_COREDUMP_FOOTER_MAGIC: Long = 0x504d5544L
private const val MIN_MEMFAULT_COREDUMP_VERSION_WITH_FOOTER: Long = 2L
private const val MEMFAULT_COREDUMP_HEADER_SIZE: Long = 12L
private const val MEMFAULT_COREDUMP_FOOTER_SIZE: Int = 16
private const val MIN_MEMFAULT_COREDUMP_SIZE: Long =
  MEMFAULT_COREDUMP_HEADER_SIZE + MEMFAULT_COREDUMP_FOOTER_SIZE
// Largest coredump storage slot size historically accepted by the app.
private const val MAX_SUPPORTED_COREDUMP_SIZE: Long = 4096L
private const val BYTE_MASK: Long = 0xffL
