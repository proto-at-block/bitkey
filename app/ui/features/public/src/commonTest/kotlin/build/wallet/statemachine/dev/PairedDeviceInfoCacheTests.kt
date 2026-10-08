package build.wallet.statemachine.dev

import build.wallet.coroutines.turbine.turbines
import build.wallet.firmware.FirmwareDeviceInfoDaoMock
import build.wallet.firmware.FirmwareDeviceInfoMock
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The debug menu may tap a bench device that isn't paired with the account. Writing that
 * device's info into [FirmwareDeviceInfoDao] would replace the paired identity that the NFC
 * `validateHardwareIsPaired` interceptor compares against, making later taps of the real
 * hardware fail as "unpaired".
 */
class PairedDeviceInfoCacheTests : FunSpec({

  val dao = FirmwareDeviceInfoDaoMock(turbines::create)

  beforeTest {
    dao.reset()
  }

  test("writes when no device info is stored yet") {
    dao.setDeviceInfoIfPaired(FirmwareDeviceInfoMock)

    dao.getDeviceInfo().get().shouldBe(FirmwareDeviceInfoMock)
  }

  test("writes when the tapped device is the paired device") {
    dao.setDeviceInfo(FirmwareDeviceInfoMock)

    val updated = FirmwareDeviceInfoMock.copy(version = "9.9.9")
    dao.setDeviceInfoIfPaired(updated)

    // Same serial, so this is a firmware version refresh of the paired device.
    dao.getDeviceInfo().get().shouldBe(updated)
  }

  test("skips the write when the tapped device is not the paired device") {
    dao.setDeviceInfo(FirmwareDeviceInfoMock)

    dao.setDeviceInfoIfPaired(FirmwareDeviceInfoMock.copy(serial = "bench-device-serial"))

    dao.getDeviceInfo().get().shouldBe(FirmwareDeviceInfoMock)
  }
})
