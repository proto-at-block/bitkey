package build.wallet.fwup

import app.cash.turbine.test
import build.wallet.coroutines.turbine.turbines
import build.wallet.debug.DebugOptionsServiceFake
import build.wallet.firmware.FirmwareDeviceInfoDaoMock
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.UpToDate
import build.wallet.fwup.FirmwareDownloadError.NoUpdateNeeded
import build.wallet.fwup.FwupDataFetcher.FwupDataFetcherError.DownloadError
import build.wallet.platform.app.AppSessionManagerFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.hours

class FirmwareDataServiceImplTests : FunSpec({

  coroutineTestScope = true

  val firmwareDeviceInfoDao =
    FirmwareDeviceInfoDaoMock(turbines::create)
  val fwupDataFetcher = FwupDataFetcherMock(turbines::create)
  val fwupDataDao = FwupDataDaoMock(turbines::create)

  val appSessionManager = AppSessionManagerFake()
  val debugOptionsService = DebugOptionsServiceFake()

  lateinit var service: FirmwareDataServiceImpl

  beforeTest {
    service =
      FirmwareDataServiceImpl(
        firmwareDeviceInfoDao = firmwareDeviceInfoDao,
        fwupDataFetcher = fwupDataFetcher,
        fwupDataDao = fwupDataDao,
        appSessionManager = appSessionManager,
        debugOptionsService = debugOptionsService
      )
    firmwareDeviceInfoDao.reset()
    fwupDataDao.reset(testName = it.name.testName)
    fwupDataFetcher.reset(testName = it.name.testName)
    appSessionManager.reset()
    debugOptionsService.reset()

    debugOptionsService.setIsHardwareFake(false)
  }

  test(
    "executeWork establishes polling for fwup data and re-syncs every hour"
  ) {
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)
    runTest {
      backgroundScope.launch {
        service.executeWork()
      }

      runCurrent()
      fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
      fwupDataDao.setFwupDataCalls.awaitItem()

      advanceTimeBy(1.hours)
      // emit again after the polling duration
      fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
      fwupDataDao.setFwupDataCalls.awaitItem()
    }
  }

  test("executeWork when isHardwareFake populates cache with fake data") {
    val info = FirmwareDeviceInfoMock
    firmwareDeviceInfoDao.setDeviceInfo(info)
    debugOptionsService.setIsHardwareFake(true)

    service.firmwareData().test {
      backgroundScope.launch {
        service.executeWork()
      }

      // Initial emission
      awaitItem()

      awaitItem().shouldBe(FirmwareDataServiceImpl.fakeFirmwareData)
    }
  }

  test("syncer does not run when fake hardware") {
    val info = FirmwareDeviceInfoMock
    firmwareDeviceInfoDao.setDeviceInfo(info)
    debugOptionsService.setIsHardwareFake(true)

    runTest {
      backgroundScope.launch {
        service.executeWork()
      }

      runCurrent()
      fwupDataFetcher.fetchLatestFwupDataCalls.expectNoEvents()
    }
  }

  test("syncer doesn't run in the background") {
    val info = FirmwareDeviceInfoMock
    firmwareDeviceInfoDao.setDeviceInfo(info)

    runTest {
      appSessionManager.appDidEnterBackground()

      backgroundScope.launch {
        service.executeWork()
      }

      advanceTimeBy(1.hours)
      fwupDataFetcher.fetchLatestFwupDataCalls.expectNoEvents()

      appSessionManager.appDidEnterForeground()
      advanceTimeBy(1.hours)
      fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
      fwupDataDao.setFwupDataCalls.awaitItem()
    }
  }

  test("changing deviceInfo triggers a sync") {
    val info = FirmwareDeviceInfoMock
    firmwareDeviceInfoDao.setDeviceInfo(info)

    runTest {
      backgroundScope.launch {
        service.executeWork()
      }

      runCurrent()
      fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
      fwupDataDao.setFwupDataCalls.awaitItem()

      firmwareDeviceInfoDao.setDeviceInfo(info.copy(version = "new-version"))
      fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
      fwupDataDao.setFwupDataCalls.awaitItem()
    }
  }

  test("updateFirmwareVersion updates device info and clears fwup") {
    // No device info clears the fwup dao
    service.updateFirmwareVersion(fwupData = FwupDataMock)
    fwupDataDao.clearCalls.awaitItem()

    // With device info sets the new version and clears the fwup dao
    val info = FirmwareDeviceInfoMock
    firmwareDeviceInfoDao.setDeviceInfo(info)

    service.updateFirmwareVersion(fwupData = FwupDataMock)

    // device info updated with new version
    firmwareDeviceInfoDao.getDeviceInfo().get()
      .shouldBe(info.copy(version = "fake"))
    fwupDataDao.clearCalls.awaitItem()
  }

  test("updateFirmwareVersion when isHardwareFake does nothing") {
    val info = FirmwareDeviceInfoMock
    firmwareDeviceInfoDao.setDeviceInfo(info)
    debugOptionsService.setIsHardwareFake(true)

    runTest {
      backgroundScope.launch {
        service.executeWork()
      }

      runCurrent()

      // No device info clears the fwup dao
      service.updateFirmwareVersion(fwupData = FwupDataMock)
      fwupDataDao.clearCalls.expectNoEvents()
    }
  }

  test("firmwareData updates when deviceInfo or fwUp changes") {
    service.firmwareData().test {
      backgroundScope.launch {
        service.executeWork()
      }
      // Initial value
      awaitItem().shouldNotBeNull().apply {
        firmwareUpdateState.shouldBe(UpToDate)
        firmwareDeviceInfo.shouldBe(null)
      }

      val info = FirmwareDeviceInfoMock
      firmwareDeviceInfoDao.setDeviceInfo(info)

      // Capture the periodic sync work
      fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
      fwupDataDao.setFwupDataCalls.awaitItem()

      // firmwareInfo set to info
      awaitItem().shouldNotBeNull().apply {
        firmwareUpdateState.shouldBe(UpToDate)
        firmwareDeviceInfo.shouldBe(info)
      }

      // fwup changed
      fwupDataDao.fwupDataFlow.value = Ok(FwupDataMock)

      awaitItem().shouldNotBeNull().apply {
        firmwareUpdateState.shouldBe(PendingUpdate(FwupDataMock))
      }
    }
  }

  test("syncLatestFwupData when isHardwareFake does nothing") {
    val info = FirmwareDeviceInfoMock
    firmwareDeviceInfoDao.setDeviceInfo(info)
    debugOptionsService.setIsHardwareFake(true)

    runTest {
      backgroundScope.launch {
        service.executeWork()
      }

      runCurrent()

      // No device info clears the fwup dao
      service.syncLatestFwupData()
      fwupDataFetcher.fetchLatestFwupDataCalls.expectNoEvents()
    }
  }

  test("syncLatestFwupData stores new FwupData") {
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)

    val fwupData = FwupDataMock.copy(version = "new-version")
    fwupDataFetcher.fetchLatestFwupDataResult = Ok(fwupData)

    service.syncLatestFwupData()

    with(fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()) {
      this.info.shouldBe(FirmwareDeviceInfoMock)
    }
    fwupDataDao.setFwupDataCalls.awaitItem().shouldBe(fwupData)
  }

  test("syncLatestFwupData clears FwupDataDao for NoUpdateNeeded error") {
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)
    fwupDataFetcher.fetchLatestFwupDataResult = Err(DownloadError(NoUpdateNeeded))

    service.syncLatestFwupData()

    fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
    fwupDataDao.clearCalls.awaitItem()
  }

  test("syncLatestFwupData doesn't do anything for other DownloadErrors") {
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)
    fwupDataFetcher.fetchLatestFwupDataResult =
      Err(DownloadError(FirmwareDownloadError.DownloadError(Throwable())))

    service.syncLatestFwupData()

    fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
    fwupDataDao.clearCalls.expectNoEvents()
  }
})
