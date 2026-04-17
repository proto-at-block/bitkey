package build.wallet.logging

import build.wallet.account.analytics.AppInstallation
import build.wallet.account.analytics.AppInstallationDaoMock
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.turbines
import build.wallet.firmware.FirmwareDeviceInfoDaoMock
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.platform.app.AppSessionManagerFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LogWriterContextStoreImplTests : FunSpec({
  val firmwareDeviceInfoDao = FirmwareDeviceInfoDaoMock(turbines::create)

  beforeTest {
    firmwareDeviceInfoDao.reset()
  }

  test("get includes current app session id") {
    val store =
      LogWriterContextStoreImpl(
        appInstallationDao = AppInstallationDaoMock(),
        appSessionManager = AppSessionManagerFake(sessionId = "session-1"),
        firmwareDeviceInfoDao = firmwareDeviceInfoDao,
        appCoroutineScope = createBackgroundScope()
      )

    store.get().appSessionId shouldBe "session-1"
  }

  test("get preserves synced context values") {
    val appInstallationDao =
      AppInstallationDaoMock().apply {
        appInstallation =
          AppInstallation(
            localId = "install-1",
            hardwareSerialNumber = "serial-1"
          )
      }
    val appSessionManager = AppSessionManagerFake(sessionId = "session-1")
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)

    val store =
      LogWriterContextStoreImpl(
        appInstallationDao = appInstallationDao,
        appSessionManager = appSessionManager,
        firmwareDeviceInfoDao = firmwareDeviceInfoDao,
        appCoroutineScope = createBackgroundScope()
      )

    store.syncContext()

    store.get().shouldBe(
      LogWriterContext(
        appInstallationId = "install-1",
        appSessionId = "session-1",
        hardwareSerialNumber = "serial-1",
        firmwareVersion = "1.2.3"
      )
    )
  }

  test("get reflects updated app session id") {
    val appSessionManager = AppSessionManagerFake(sessionId = "session-1")
    val store =
      LogWriterContextStoreImpl(
        appInstallationDao = AppInstallationDaoMock(),
        appSessionManager = appSessionManager,
        firmwareDeviceInfoDao = firmwareDeviceInfoDao,
        appCoroutineScope = createBackgroundScope()
      )

    store.get().appSessionId shouldBe "session-1"

    appSessionManager.currentSessionId = "session-2"

    store.get().appSessionId shouldBe "session-2"
  }

  test("context updates reactively when hardware serial number changes") {
    val appInstallationDao =
      AppInstallationDaoMock().apply {
        appInstallation =
          AppInstallation(
            localId = "install-1",
            hardwareSerialNumber = null
          )
      }

    val store =
      LogWriterContextStoreImpl(
        appInstallationDao = appInstallationDao,
        appSessionManager = AppSessionManagerFake(sessionId = "session-1"),
        firmwareDeviceInfoDao = firmwareDeviceInfoDao,
        appCoroutineScope = createBackgroundScope()
      )

    store.syncContext()
    store.get().hardwareSerialNumber shouldBe null

    // Simulate serial update (e.g. after pairing or cloud backup restoration)
    appInstallationDao.appInstallation =
      AppInstallation(
        localId = "install-1",
        hardwareSerialNumber = "new-serial"
      )

    store.get().hardwareSerialNumber shouldBe "new-serial"
  }

  test("context updates reactively when firmware version changes") {
    val store =
      LogWriterContextStoreImpl(
        appInstallationDao = AppInstallationDaoMock(),
        appSessionManager = AppSessionManagerFake(sessionId = "session-1"),
        firmwareDeviceInfoDao = firmwareDeviceInfoDao,
        appCoroutineScope = createBackgroundScope()
      )

    store.syncContext()
    store.get().firmwareVersion shouldBe null

    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)

    store.get().firmwareVersion shouldBe "1.2.3"
  }
})
