package build.wallet.firmware

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import okio.ByteString.Companion.toByteString

class FirmwareTelemetryUploaderImplTests : FunSpec({
  test("saves coredumps using the header total size") {
    val appCoroutineScope = TestScope()
    val coredumpProcessor = RecordingFirmwareCoredumpEventProcessor()
    val uploader = firmwareTelemetryUploader(appCoroutineScope, coredumpProcessor)

    listOf(684, 588, 4096).forEach { size ->
      uploader.addCoredump(memfaultCoredump(size), identifiers)
    }
    appCoroutineScope.advanceUntilIdle()

    coredumpProcessor.processed.map { it.coredump.size }
      .shouldBe(listOf(684, 588, 4096))
  }

  test("allows future Memfault versions that keep the same header and footer contract") {
    val appCoroutineScope = TestScope()
    val coredumpProcessor = RecordingFirmwareCoredumpEventProcessor()
    val uploader = firmwareTelemetryUploader(appCoroutineScope, coredumpProcessor)

    uploader.addCoredump(memfaultCoredump(storageSize = 684, version = 3), identifiers)
    appCoroutineScope.advanceUntilIdle()

    coredumpProcessor.processed.single().coredump.size.shouldBe(684)
  }

  test("trims padded coredump storage to the header total size") {
    val appCoroutineScope = TestScope()
    val coredumpProcessor = RecordingFirmwareCoredumpEventProcessor()
    val uploader = firmwareTelemetryUploader(appCoroutineScope, coredumpProcessor)

    uploader.addCoredump(
      memfaultCoredump(
        storageSize = 4096,
        totalSize = 588
      ),
      identifiers
    )
    appCoroutineScope.advanceUntilIdle()

    coredumpProcessor.processed.single().coredump.size.shouldBe(588)
  }

  test("rejects coredumps with invalid headers") {
    val appCoroutineScope = TestScope()
    val coredumpProcessor = RecordingFirmwareCoredumpEventProcessor()
    val uploader = firmwareTelemetryUploader(appCoroutineScope, coredumpProcessor)

    uploader.addCoredump(ByteArray(MEMFAULT_COREDUMP_HEADER_SIZE - 1).toByteString(), identifiers)
    uploader.addCoredump(memfaultCoredump(storageSize = 684, magic = 0), identifiers)
    uploader.addCoredump(memfaultCoredump(storageSize = 684, version = 1), identifiers)
    uploader.addCoredump(
      memfaultCoredump(
        storageSize = 683,
        totalSize = 684
      ),
      identifiers
    )
    appCoroutineScope.advanceUntilIdle()

    coredumpProcessor.processed.shouldBe(emptyList())
  }

  test("rejects malformed or over-limit coredumps") {
    val appCoroutineScope = TestScope()
    val coredumpProcessor = RecordingFirmwareCoredumpEventProcessor()
    val uploader = firmwareTelemetryUploader(appCoroutineScope, coredumpProcessor)

    uploader.addCoredump(
      memfaultCoredump(
        storageSize = MEMFAULT_COREDUMP_HEADER_SIZE,
        totalSize = MEMFAULT_COREDUMP_HEADER_SIZE
      ),
      identifiers
    )
    uploader.addCoredump(
      memfaultCoredump(
        storageSize = 684,
        footerMagic = 0
      ),
      identifiers
    )
    uploader.addCoredump(
      memfaultCoredump(
        storageSize = MAX_SUPPORTED_COREDUMP_SIZE + 1,
        totalSize = MAX_SUPPORTED_COREDUMP_SIZE + 1
      ),
      identifiers
    )
    appCoroutineScope.advanceUntilIdle()

    coredumpProcessor.processed.shouldBe(emptyList())
  }
})

private val identifiers =
  TelemetryIdentifiers(
    serial = "W1A0000000000001",
    version = "1.0.0",
    swType = "app-a-dev",
    hwRevision = "w1a-dvt",
    mcuInfo = "CORE:EFR32:1.0.0"
  )

private fun firmwareTelemetryUploader(
  appCoroutineScope: TestScope,
  coredumpProcessor: FirmwareCoredumpEventProcessor,
) = FirmwareTelemetryUploaderImpl(
  appCoroutineScope = appCoroutineScope,
  firmwareCoredumpProcessor = coredumpProcessor,
  firmwareTelemetryProcessor = NoOpFirmwareTelemetryEventProcessor,
  teltra = NoOpTeltra
)

private class RecordingFirmwareCoredumpEventProcessor : FirmwareCoredumpEventProcessor {
  val processed = mutableListOf<FirmwareCoredump>()

  override suspend fun processBatch(batch: List<FirmwareCoredump>): Result<Unit, Error> {
    processed.addAll(batch)
    return Ok(Unit)
  }
}

private object NoOpFirmwareTelemetryEventProcessor : FirmwareTelemetryEventProcessor {
  override suspend fun processBatch(batch: List<FirmwareTelemetryEvent>): Result<Unit, Error> {
    return Ok(Unit)
  }
}

private object NoOpTeltra : Teltra {
  override fun translateBitlogs(
    bitlogs: List<UByte>,
    identifiers: TelemetryIdentifiers,
  ): List<List<UByte>> {
    return emptyList()
  }
}

private fun memfaultCoredump(
  storageSize: Int,
  totalSize: Int = storageSize,
  magic: Long = MEMFAULT_COREDUMP_MAGIC,
  version: Long = MEMFAULT_COREDUMP_VERSION,
  footerMagic: Long = MEMFAULT_COREDUMP_FOOTER_MAGIC,
) = ByteArray(storageSize)
  .apply {
    writeUInt32Le(offset = 0, value = magic)
    writeUInt32Le(offset = 4, value = version)
    writeUInt32Le(offset = 8, value = totalSize.toLong())
    if (totalSize >= MEMFAULT_COREDUMP_HEADER_SIZE + MEMFAULT_COREDUMP_FOOTER_SIZE) {
      writeUInt32Le(offset = totalSize - MEMFAULT_COREDUMP_FOOTER_SIZE, value = footerMagic)
    }
  }
  .toByteString()

private fun ByteArray.writeUInt32Le(
  offset: Int,
  value: Long,
) {
  this[offset] = value.toByte()
  this[offset + 1] = (value shr 8).toByte()
  this[offset + 2] = (value shr 16).toByte()
  this[offset + 3] = (value shr 24).toByte()
}

private const val MEMFAULT_COREDUMP_MAGIC: Long = 0x45524f43L
private const val MEMFAULT_COREDUMP_FOOTER_MAGIC: Long = 0x504d5544L
private const val MEMFAULT_COREDUMP_VERSION: Long = 2L
private const val MEMFAULT_COREDUMP_HEADER_SIZE: Int = 12
private const val MEMFAULT_COREDUMP_FOOTER_SIZE: Int = 16
private const val MAX_SUPPORTED_COREDUMP_SIZE: Int = 4096
