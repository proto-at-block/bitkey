package build.wallet.firmware

import build.wallet.coroutines.turbine.turbines
import build.wallet.firmware.FirmwareMetadata.FirmwareSlot
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.memfault.MemfaultClientMock
import build.wallet.memfault.MemfaultClientMock.UploadCoredumpRequest
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString

class FirmwareCoredumpEventProcessorImplTests : FunSpec({
  val memfaultClient = MemfaultClientMock(turbines::create)
  val firmwareCoredumpSender = FirmwareCoredumpEventProcessorImpl(memfaultClient)

  val coredump1 = FirmwareCoredump(ByteString.EMPTY, TelemetryIdentifiers("1", "", "", "", ""))
  val coredump2 = FirmwareCoredump(ByteString.EMPTY, TelemetryIdentifiers("2", "", "", "", ""))

  beforeTest {
    memfaultClient.reset()
  }

  test("no payloads is no work") {
    firmwareCoredumpSender.processBatch(emptyList()).unwrap()
  }

  test("correct delegates to memfault service") {
    memfaultClient.uploadCoredumpReturns = listOf(Ok(Unit), Ok(Unit))

    firmwareCoredumpSender.processBatch(listOf(coredump1, coredump2)).unwrap()

    memfaultClient.uploadCoredumpCalls.awaitItem().shouldBe(
      UploadCoredumpRequest(
        deviceSerial = coredump1.identifiers.serial,
        hardwareVersion = coredump1.identifiers.hwRevision,
        softwareType = coredump1.identifiers.swType,
        softwareVersion = coredump1.identifiers.version
      )
    )
    memfaultClient.uploadCoredumpCalls.awaitItem().shouldBe(
      UploadCoredumpRequest(
        deviceSerial = coredump2.identifiers.serial,
        hardwareVersion = coredump2.identifiers.hwRevision,
        softwareType = coredump2.identifiers.swType,
        softwareVersion = coredump2.identifiers.version
      )
    )
  }

  test("uses scoped W3 MCU identifiers") {
    memfaultClient.uploadCoredumpReturns = listOf(Ok(Unit))
    val coredump =
      FirmwareCoredump(
        ByteString.EMPTY,
        TelemetryIdentifiers(
          serial = "W3A0000000000001",
          version = "1.2.3",
          swType = "app-a-dev",
          hwRevision = "w3a-core-pdvt",
          mcuInfo = "UXC:STM32U5:1.2.3",
          mcuRole = McuRole.UXC,
          activeSlot = FirmwareSlot.B
        )
      )

    firmwareCoredumpSender.processBatch(listOf(coredump)).unwrap()

    memfaultClient.uploadCoredumpCalls.awaitItem().shouldBe(
      UploadCoredumpRequest(
        deviceSerial = "W3A0000000000001",
        hardwareVersion = "w3a-uxc-pdvt",
        softwareType = "app-b-dev",
        softwareVersion = "1.2.3"
      )
    )
  }

  test("failure on first upload propagates") {
    val error = Err(NetworkError(Throwable("Uh oh!")))
    memfaultClient.uploadCoredumpReturns = listOf(error)

    firmwareCoredumpSender.processBatch(listOf(coredump1, coredump2)).shouldBe(error)

    memfaultClient.uploadCoredumpCalls.awaitItem().shouldBe(
      UploadCoredumpRequest(
        deviceSerial = coredump1.identifiers.serial,
        hardwareVersion = coredump1.identifiers.hwRevision,
        softwareType = coredump1.identifiers.swType,
        softwareVersion = coredump1.identifiers.version
      )
    )
  }

  test("failure on second upload propagates") {
    val error = Err(NetworkError(Throwable("Uh oh!")))
    memfaultClient.uploadCoredumpReturns = listOf(Ok(Unit), error)

    firmwareCoredumpSender.processBatch(listOf(coredump1, coredump2)).shouldBe(error)

    memfaultClient.uploadCoredumpCalls.awaitItem().shouldBe(
      UploadCoredumpRequest(
        deviceSerial = coredump1.identifiers.serial,
        hardwareVersion = coredump1.identifiers.hwRevision,
        softwareType = coredump1.identifiers.swType,
        softwareVersion = coredump1.identifiers.version
      )
    )
    memfaultClient.uploadCoredumpCalls.awaitItem().shouldBe(
      UploadCoredumpRequest(
        deviceSerial = coredump2.identifiers.serial,
        hardwareVersion = coredump2.identifiers.hwRevision,
        softwareType = coredump2.identifiers.swType,
        softwareVersion = coredump2.identifiers.version
      )
    )
  }
})
