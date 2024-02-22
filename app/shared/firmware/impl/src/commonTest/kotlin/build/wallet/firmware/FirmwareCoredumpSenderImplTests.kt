package build.wallet.firmware

import build.wallet.coroutines.turbine.turbines
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.memfault.MemfaultServiceMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString

class FirmwareCoredumpSenderImplTests : FunSpec({
  val memfaultServiceMock = MemfaultServiceMock(turbines::create)
  val firmwareCoredumpSender = FirmwareCoredumpSenderImpl(memfaultServiceMock)

  val coredump1 = FirmwareCoredump(ByteString.EMPTY, TelemetryIdentifiers("1", "", "", ""))
  val coredump2 = FirmwareCoredump(ByteString.EMPTY, TelemetryIdentifiers("2", "", "", ""))

  beforeTest {
    memfaultServiceMock.reset()
  }

  test("no payloads is no work") {
    firmwareCoredumpSender.processBatch(emptyList()).unwrap()
  }

  test("correct delegates to memfault service") {
    memfaultServiceMock.uploadCoredumpReturns = listOf(Ok(Unit), Ok(Unit))

    firmwareCoredumpSender.processBatch(listOf(coredump1, coredump2)).unwrap()

    memfaultServiceMock.uploadCoredumpCalls.awaitItem().shouldBe(coredump1.identifiers.serial)
    memfaultServiceMock.uploadCoredumpCalls.awaitItem().shouldBe(coredump2.identifiers.serial)
  }

  test("failure on first upload propagates") {
    val error = Err(NetworkError(Throwable("Uh oh!")))
    memfaultServiceMock.uploadCoredumpReturns = listOf(error)

    firmwareCoredumpSender.processBatch(listOf(coredump1, coredump2)).shouldBe(error)

    memfaultServiceMock.uploadCoredumpCalls.awaitItem().shouldBe(coredump1.identifiers.serial)
  }

  test("failure on second upload propagates") {
    val error = Err(NetworkError(Throwable("Uh oh!")))
    memfaultServiceMock.uploadCoredumpReturns = listOf(Ok(Unit), error)

    firmwareCoredumpSender.processBatch(listOf(coredump1, coredump2)).shouldBe(error)

    memfaultServiceMock.uploadCoredumpCalls.awaitItem().shouldBe(coredump1.identifiers.serial)
    memfaultServiceMock.uploadCoredumpCalls.awaitItem().shouldBe(coredump2.identifiers.serial)
  }
})
