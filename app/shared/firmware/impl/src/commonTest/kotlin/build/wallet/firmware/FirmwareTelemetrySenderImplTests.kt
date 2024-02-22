package build.wallet.firmware

import build.wallet.coroutines.turbine.turbines
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.memfault.MemfaultService.UploadTelemetryEventSuccess
import build.wallet.memfault.MemfaultServiceMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class FirmwareTelemetrySenderImplTests : FunSpec({
  val memfaultServiceMock = MemfaultServiceMock(turbines::create)
  val firmwareTelemetrySender = FirmwareTelemetrySenderImpl(memfaultServiceMock)

  val telemetry1 = FirmwareTelemetryEvent("1", "1".encodeUtf8())
  val telemetry2 = FirmwareTelemetryEvent("2", "2".encodeUtf8())

  beforeTest {
    memfaultServiceMock.reset()
  }

  test("no payloads is no work") {
    firmwareTelemetrySender.processBatch(emptyList()).unwrap()
  }

  test("correct delegates to memfault service") {
    memfaultServiceMock.uploadTelemetryEventReturns = listOf(Ok(UploadTelemetryEventSuccess), Ok(UploadTelemetryEventSuccess))

    firmwareTelemetrySender.processBatch(listOf(telemetry1, telemetry2)).unwrap()

    memfaultServiceMock.uploadTelemetryEventCalls.awaitItem().shouldBe(telemetry1.serial)
    memfaultServiceMock.uploadTelemetryEventCalls.awaitItem().shouldBe(telemetry2.serial)
  }

  test("failure on first upload propagates") {
    val error = Err(NetworkError(Throwable("Uh oh!")))
    memfaultServiceMock.uploadTelemetryEventReturns = listOf(error)

    firmwareTelemetrySender.processBatch(listOf(telemetry1, telemetry2)).shouldBe(error)

    memfaultServiceMock.uploadTelemetryEventCalls.awaitItem().shouldBe(telemetry1.serial)
  }

  test("failure on second upload propagates") {
    val error = Err(NetworkError(Throwable("Uh oh!")))
    memfaultServiceMock.uploadTelemetryEventReturns = listOf(Ok(UploadTelemetryEventSuccess), error)

    firmwareTelemetrySender.processBatch(listOf(telemetry1, telemetry2)).shouldBe(error)

    memfaultServiceMock.uploadTelemetryEventCalls.awaitItem().shouldBe(telemetry1.serial)
    memfaultServiceMock.uploadTelemetryEventCalls.awaitItem().shouldBe(telemetry2.serial)
  }
})
