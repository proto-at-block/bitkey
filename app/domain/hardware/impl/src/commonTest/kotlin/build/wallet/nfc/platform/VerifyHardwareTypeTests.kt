package build.wallet.nfc.platform

import bitkey.account.HardwareType
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcSessionFake
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class VerifyHardwareTypeTests : FunSpec({
  val nfcCommands = NfcCommandsMock(turbine = { name ->
    app.cash.turbine.Turbine(name = name)
  })
  val session = NfcSessionFake()

  beforeTest {
    nfcCommands.reset()
  }

  test("verifyHardwareType succeeds when device matches expected W1 type") {
    // FirmwareDeviceInfoMock defaults to hwRevision="evtd" which is W1
    nfcCommands.deviceInfoResult = FirmwareDeviceInfoMock

    shouldNotThrowAny {
      nfcCommands.verifyHardwareType(session, expectedType = HardwareType.W1)
    }
  }

  test("verifyHardwareType succeeds when device matches expected W3 type") {
    nfcCommands.deviceInfoResult = FirmwareDeviceInfoMock.copy(hwRevision = "w3a-core-evt")

    shouldNotThrowAny {
      nfcCommands.verifyHardwareType(session, expectedType = HardwareType.W3)
    }
  }

  test("verifyHardwareType throws WrongHardwareType when W3 expected but W1 tapped") {
    nfcCommands.deviceInfoResult = FirmwareDeviceInfoMock // W1

    val exception = shouldThrow<NfcException.WrongHardwareType> {
      nfcCommands.verifyHardwareType(session, expectedType = HardwareType.W3)
    }
    exception.expected.shouldBe(HardwareType.W3)
    exception.actual.shouldBe(HardwareType.W1)
  }

  test("verifyHardwareType throws WrongHardwareType when W1 expected but W3 tapped") {
    nfcCommands.deviceInfoResult = FirmwareDeviceInfoMock.copy(hwRevision = "w3a-core-evt")

    val exception = shouldThrow<NfcException.WrongHardwareType> {
      nfcCommands.verifyHardwareType(session, expectedType = HardwareType.W1)
    }
    exception.expected.shouldBe(HardwareType.W1)
    exception.actual.shouldBe(HardwareType.W3)
  }
})
