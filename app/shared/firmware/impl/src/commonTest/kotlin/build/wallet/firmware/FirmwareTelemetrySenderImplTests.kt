package build.wallet.firmware

import build.wallet.coroutines.turbine.turbines
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.memfault.MemfaultClient.UploadTelemetryEventSuccess
import build.wallet.memfault.MemfaultClientMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class FirmwareTelemetrySenderImplTests : FunSpec({
  val memfaultClient = MemfaultClientMock(turbines::create)
  val firmwareTelemetrySender = FirmwareTelemetrySenderImpl(memfaultClient)

  val telemetry1 = FirmwareTelemetryEvent("1", "1".encodeUtf8())
  val telemetry2 = FirmwareTelemetryEvent("2", "2".encodeUtf8())

  beforeTest {
    memfaultClient.reset()
  }

  test("no payloads is no work") {
    firmwareTelemetrySender.processBatch(emptyList()).unwrap()
  }

  test("correct delegates to memfault service") {
    memfaultClient.uploadTelemetryEventReturns = listOf(Ok(UploadTelemetryEventSuccess), Ok(UploadTelemetryEventSuccess))

    firmwareTelemetrySender.processBatch(listOf(telemetry1, telemetry2)).unwrap()

    memfaultClient.uploadTelemetryEventCalls.awaitItem().shouldBe(telemetry1.serial)
    memfaultClient.uploadTelemetryEventCalls.awaitItem().shouldBe(telemetry2.serial)
  }

  test("failure on first upload propagates") {
    val error = Err(NetworkError(Throwable("Uh oh!")))
    memfaultClient.uploadTelemetryEventReturns = listOf(error)

    firmwareTelemetrySender.processBatch(listOf(telemetry1, telemetry2)).shouldBe(error)

    memfaultClient.uploadTelemetryEventCalls.awaitItem().shouldBe(telemetry1.serial)
  }

  test("failure on second upload propagates") {
    val error = Err(NetworkError(Throwable("Uh oh!")))
    memfaultClient.uploadTelemetryEventReturns = listOf(Ok(UploadTelemetryEventSuccess), error)

    firmwareTelemetrySender.processBatch(listOf(telemetry1, telemetry2)).shouldBe(error)

    memfaultClient.uploadTelemetryEventCalls.awaitItem().shouldBe(telemetry1.serial)
    memfaultClient.uploadTelemetryEventCalls.awaitItem().shouldBe(telemetry2.serial)
  }
})
