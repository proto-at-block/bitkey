package build.wallet.fwup

import app.cash.turbine.test
import bitkey.account.AccountConfigServiceFake
import build.wallet.coroutines.turbine.turbines
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
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours

class FirmwareDataServiceImplTests : FunSpec({
  // TODO(W-10571): use real dispatcher.
  coroutineTestScope = true

  val firmwareDeviceInfoDao =
    FirmwareDeviceInfoDaoMock(turbines::create)
  val fwupDataFetcher = FwupDataFetcherMock(turbines::create)
  val fwupDataDaoProvider = FwupDataDaoProviderMock(turbines::create)

  val appSessionManager = AppSessionManagerFake()
  val defaultAppConfigService = AccountConfigServiceFake()

  lateinit var service: FirmwareDataServiceImpl

  beforeTest {
    service = FirmwareDataServiceImpl(
      firmwareDeviceInfoDao = firmwareDeviceInfoDao,
      fwupDataFetcher = fwupDataFetcher,
      fwupDataDaoProvider = fwupDataDaoProvider,
      appSessionManager = appSessionManager,
      firmwareUpdateSyncFrequency = FirmwareUpdateSyncFrequency()
    )
    firmwareDeviceInfoDao.reset()
    fwupDataDaoProvider.reset(testName = it.name.testName)
    fwupDataFetcher.reset(testName = it.name.testName)
    appSessionManager.reset()
    defaultAppConfigService.reset()

    defaultAppConfigService.setIsHardwareFake(false)
  }

  test("executeWork establishes polling for fwup data and re-syncs every hour") {
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)
    backgroundScope.launch {
      service.executeWork()
    }

    testCoroutineScheduler.runCurrent()
    fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
    fwupDataDaoProvider.fwupDataDaoMock.setFwupDataCalls.awaitItem()

    testCoroutineScheduler.advanceTimeBy(1.hours)
    // emit again after the polling duration
    fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
    fwupDataDaoProvider.fwupDataDaoMock.setFwupDataCalls.awaitItem()
  }

  test("syncer doesn't run in the background") {
    val info = FirmwareDeviceInfoMock
    firmwareDeviceInfoDao.setDeviceInfo(info)

    appSessionManager.appDidEnterBackground()

    backgroundScope.launch {
      service.executeWork()
    }

    testCoroutineScheduler.advanceTimeBy(1.hours)
    fwupDataFetcher.fetchLatestFwupDataCalls.expectNoEvents()

    appSessionManager.appDidEnterForeground()
    testCoroutineScheduler.advanceTimeBy(1.hours)
    fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
    fwupDataDaoProvider.fwupDataDaoMock.setFwupDataCalls.awaitItem()
  }

  test("changing deviceInfo triggers a sync") {
    val info = FirmwareDeviceInfoMock
    firmwareDeviceInfoDao.setDeviceInfo(info)

    backgroundScope.launch {
      service.executeWork()
    }

    testCoroutineScheduler.runCurrent()
    fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
    fwupDataDaoProvider.fwupDataDaoMock.setFwupDataCalls.awaitItem()

    firmwareDeviceInfoDao.setDeviceInfo(info.copy(version = "new-version"))
    fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
    fwupDataDaoProvider.fwupDataDaoMock.setFwupDataCalls.awaitItem()
  }

  test("updateFirmwareVersion updates device info and clears fwup") {
    // No device info clears the fwup dao
    service.updateFirmwareVersion(fwupData = FwupDataMock)
    fwupDataDaoProvider.fwupDataDaoMock.clearCalls.awaitItem()

    // With device info sets the new version and clears the fwup dao
    val info = FirmwareDeviceInfoMock
    firmwareDeviceInfoDao.setDeviceInfo(info)

    service.updateFirmwareVersion(fwupData = FwupDataMock)

    // device info updated with new version
    firmwareDeviceInfoDao.getDeviceInfo().get()
      .shouldBe(info.copy(version = "fake"))
    fwupDataDaoProvider.fwupDataDaoMock.clearCalls.awaitItem()
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
      fwupDataDaoProvider.fwupDataDaoMock.setFwupDataCalls.awaitItem()

      // firmwareInfo set to info
      awaitItem().shouldNotBeNull().apply {
        firmwareUpdateState.shouldBe(UpToDate)
        firmwareDeviceInfo.shouldBe(info)
      }

      // fwup changed
      fwupDataDaoProvider.fwupDataDaoMock.fwupDataFlow.value = Ok(FwupDataMock)

      awaitItem().shouldNotBeNull().apply {
        firmwareUpdateState.shouldBe(PendingUpdate(FwupDataMock))
      }
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
    fwupDataDaoProvider.fwupDataDaoMock.setFwupDataCalls.awaitItem().shouldBe(fwupData)
  }

  test("syncLatestFwupData clears FwupDataDao for NoUpdateNeeded error") {
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)
    fwupDataFetcher.fetchLatestFwupDataResult = Err(DownloadError(NoUpdateNeeded))

    service.syncLatestFwupData()

    fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
    fwupDataDaoProvider.fwupDataDaoMock.clearCalls.awaitItem()
  }

  test("syncLatestFwupData doesn't do anything for other DownloadErrors") {
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)
    fwupDataFetcher.fetchLatestFwupDataResult =
      Err(DownloadError(FirmwareDownloadError.DownloadError(Throwable())))

    service.syncLatestFwupData()

    fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
    fwupDataDaoProvider.fwupDataDaoMock.clearCalls.expectNoEvents()
  }

  test("firmwareData switches DAO when hardware fake setting changes") {
    // Create service with real FwupDataDaoProviderImpl to test DAO switching
    val realFwupDataDaoImpl = FwupDataDaoMock { name -> turbines.create("real-$name") }
    val realFwupDataDaoFake = FwupDataDaoMock { name -> turbines.create("fake-$name") }
    val realFwupDataDaoProvider = FwupDataDaoProviderImpl(
      fwupDataDaoImpl = realFwupDataDaoImpl,
      fwupDataDaoFake = realFwupDataDaoFake,
      accountConfigService = defaultAppConfigService,
      appCoroutineScope = backgroundScope
    )

    val serviceWithRealProvider = FirmwareDataServiceImpl(
      firmwareDeviceInfoDao = firmwareDeviceInfoDao,
      fwupDataFetcher = fwupDataFetcher,
      fwupDataDaoProvider = realFwupDataDaoProvider,
      appSessionManager = appSessionManager,
      firmwareUpdateSyncFrequency = FirmwareUpdateSyncFrequency()
    )

    serviceWithRealProvider.firmwareData().test {
      backgroundScope.launch {
        serviceWithRealProvider.executeWork()
      }

      // Initial state: real DAO
      awaitItem().shouldNotBeNull().apply {
        firmwareUpdateState.shouldBe(UpToDate)
      }

      // Set fake fwup data in the fake DAO only
      realFwupDataDaoFake.fwupDataFlow.value = Ok(FwupDataMock)

      // Switch to fake hardware - should start using fake DAO with its data
      defaultAppConfigService.setIsHardwareFake(true)
      testCoroutineScheduler.runCurrent()

      // Should now emit with the fake DAO's data
      awaitItem().shouldNotBeNull().apply {
        firmwareUpdateState.shouldBe(PendingUpdate(FwupDataMock))
      }

      // Switch back to real hardware - should use real DAO (which has no data)
      defaultAppConfigService.setIsHardwareFake(false)
      testCoroutineScheduler.runCurrent()

      // The switch to real hardware triggers a clear call on the real DAO
      realFwupDataDaoImpl.clearCalls.awaitItem()

      awaitItem().shouldNotBeNull().apply {
        firmwareUpdateState.shouldBe(UpToDate)
      }
    }
  }
})
