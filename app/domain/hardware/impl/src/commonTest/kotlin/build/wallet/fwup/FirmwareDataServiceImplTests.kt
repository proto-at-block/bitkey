package build.wallet.fwup

import app.cash.turbine.test
import bitkey.account.AccountConfigServiceFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.firmware.FirmwareDeviceInfoDaoMock
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.firmware.McuInfo
import build.wallet.firmware.McuName
import build.wallet.firmware.McuRole
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.UpToDate
import build.wallet.fwup.FirmwareDownloadError.NoUpdateNeeded
import build.wallet.fwup.FwupDataFetcher.FwupDataFetcherError.DownloadError
import build.wallet.nfc.HardwareProvisionedAppKeyStatusDaoFake
import build.wallet.platform.app.AppSessionManagerFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours

class FirmwareDataServiceImplTests : FunSpec({
  // TODO(W-10571): use real dispatcher.
  coroutineTestScope = true

  val firmwareDeviceInfoDao =
    FirmwareDeviceInfoDaoMock(turbines::create)
  val fwupDataFetcher = FwupDataFetcherMock(turbines::create)
  val fwupDataDaoProvider = FwupDataDaoProviderMock(turbines::create)
  val hardwareProvisionedAppKeyStatusDao = HardwareProvisionedAppKeyStatusDaoFake()

  val appSessionManager = AppSessionManagerFake()
  val defaultAppConfigService = AccountConfigServiceFake()

  lateinit var service: FirmwareDataServiceImpl

  beforeTest {
    service = FirmwareDataServiceImpl(
      firmwareDeviceInfoDao = firmwareDeviceInfoDao,
      fwupDataFetcher = fwupDataFetcher,
      fwupDataDaoProvider = fwupDataDaoProvider,
      appSessionManager = appSessionManager,
      firmwareUpdateSyncFrequency = FirmwareUpdateSyncFrequency(),
      hardwareProvisionedAppKeyStatusDao = hardwareProvisionedAppKeyStatusDao
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
    fwupDataDaoProvider.fwupDataDaoMock.setMcuFwupDataCalls.awaitItem()

    testCoroutineScheduler.advanceTimeBy(1.hours)
    // emit again after the polling duration
    fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
    fwupDataDaoProvider.fwupDataDaoMock.setMcuFwupDataCalls.awaitItem()
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
    fwupDataDaoProvider.fwupDataDaoMock.setMcuFwupDataCalls.awaitItem()
  }

  test("changing deviceInfo triggers a sync") {
    val info = FirmwareDeviceInfoMock
    firmwareDeviceInfoDao.setDeviceInfo(info)

    backgroundScope.launch {
      service.executeWork()
    }

    testCoroutineScheduler.runCurrent()
    fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
    fwupDataDaoProvider.fwupDataDaoMock.setMcuFwupDataCalls.awaitItem()

    firmwareDeviceInfoDao.setDeviceInfo(info.copy(version = "new-version"))
    fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
    fwupDataDaoProvider.fwupDataDaoMock.setMcuFwupDataCalls.awaitItem()
  }

  test("updateFirmwareVersion updates device info and clears fwup") {
    // No device info clears the fwup dao
    service.updateFirmwareVersion(mcuUpdates = McuFwupDataListMock_W1)
    fwupDataDaoProvider.fwupDataDaoMock.clearCalls.awaitItem()
    fwupDataDaoProvider.fwupDataDaoMock.clearAllMcuStatesCalls.awaitItem()

    // With device info sets the new version and clears the fwup dao
    val info = FirmwareDeviceInfoMock
    firmwareDeviceInfoDao.setDeviceInfo(info)

    service.updateFirmwareVersion(mcuUpdates = McuFwupDataListMock_W1)

    // device info updated with new version
    firmwareDeviceInfoDao.getDeviceInfo().get()
      .shouldBe(info.copy(version = "1.0.0-fake"))
    fwupDataDaoProvider.fwupDataDaoMock.clearCalls.awaitItem()
    fwupDataDaoProvider.fwupDataDaoMock.clearAllMcuStatesCalls.awaitItem()
  }

  test("updateFirmwareVersion with W3 multi-MCU updates both CORE and UXC versions") {
    // Set up W3 device info with existing MCU versions
    val w3DeviceInfo = FirmwareDeviceInfoMock.copy(
      version = "1.0.0",
      mcuInfo = listOf(
        McuInfo(mcuRole = McuRole.CORE, mcuName = McuName.EFR32, firmwareVersion = "1.0.0"),
        McuInfo(mcuRole = McuRole.UXC, mcuName = McuName.STM32U5, firmwareVersion = "1.0.0")
      )
    )
    firmwareDeviceInfoDao.setDeviceInfo(w3DeviceInfo)

    // Update with W3 multi-MCU data
    service.updateFirmwareVersion(mcuUpdates = McuFwupDataListMock_W3)

    // Verify device info was updated with new versions for both MCUs
    val updatedInfo = firmwareDeviceInfoDao.getDeviceInfo().get()
    updatedInfo.shouldNotBeNull()

    // Main version should be CORE version (for backwards compatibility)
    updatedInfo.version.shouldBe(McuFwupDataMock_W3_CORE.version)

    // mcuInfo should have updated versions for both MCUs
    updatedInfo.mcuInfo.size.shouldBe(2)
    updatedInfo.mcuInfo.find { it.mcuRole == McuRole.CORE }?.firmwareVersion
      .shouldBe(McuFwupDataMock_W3_CORE.version)
    updatedInfo.mcuInfo.find { it.mcuRole == McuRole.UXC }?.firmwareVersion
      .shouldBe(McuFwupDataMock_W3_UXC.version)

    // DAO should be cleared
    fwupDataDaoProvider.fwupDataDaoMock.clearCalls.awaitItem()
    fwupDataDaoProvider.fwupDataDaoMock.clearAllMcuStatesCalls.awaitItem()
  }

  test("updateFirmwareVersion with partial MCU update only updates provided MCUs") {
    // Set up W3 device info
    val w3DeviceInfo = FirmwareDeviceInfoMock.copy(
      version = "1.0.0",
      mcuInfo = listOf(
        McuInfo(mcuRole = McuRole.CORE, mcuName = McuName.EFR32, firmwareVersion = "1.0.0"),
        McuInfo(mcuRole = McuRole.UXC, mcuName = McuName.STM32U5, firmwareVersion = "1.0.0")
      )
    )
    firmwareDeviceInfoDao.setDeviceInfo(w3DeviceInfo)

    // Update only CORE (simulating partial update scenario)
    val coreOnlyUpdate = listOf(McuFwupDataMock_W3_CORE).toImmutableList()
    service.updateFirmwareVersion(mcuUpdates = coreOnlyUpdate)

    // Verify only CORE was updated, UXC remains at old version
    val updatedInfo = firmwareDeviceInfoDao.getDeviceInfo().get()
    updatedInfo.shouldNotBeNull()

    updatedInfo.version.shouldBe(McuFwupDataMock_W3_CORE.version)
    updatedInfo.mcuInfo.find { it.mcuRole == McuRole.CORE }?.firmwareVersion
      .shouldBe(McuFwupDataMock_W3_CORE.version)
    updatedInfo.mcuInfo.find { it.mcuRole == McuRole.UXC }?.firmwareVersion
      .shouldBe("1.0.0") // Unchanged

    fwupDataDaoProvider.fwupDataDaoMock.clearCalls.awaitItem()
    fwupDataDaoProvider.fwupDataDaoMock.clearAllMcuStatesCalls.awaitItem()
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
      fwupDataDaoProvider.fwupDataDaoMock.setMcuFwupDataCalls.awaitItem()

      // firmwareInfo set to info, with pending update from sync
      awaitItem().shouldNotBeNull().apply {
        val expectedMcuData = listOf(McuFwupDataMock).toImmutableList()
        firmwareUpdateState.shouldBe(PendingUpdate(mcuUpdates = expectedMcuData))
        firmwareDeviceInfo.shouldBe(info)
      }
    }
  }

  test("syncLatestFwupData stores new McuFwupData") {
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)

    val mcuFwupData = listOf(McuFwupDataMock.copy(version = "new-version"))
    fwupDataFetcher.fetchLatestFwupDataResult = Ok(mcuFwupData)

    service.syncLatestFwupData()

    with(fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()) {
      this.info.shouldBe(FirmwareDeviceInfoMock)
    }
    fwupDataDaoProvider.fwupDataDaoMock.setMcuFwupDataCalls.awaitItem().shouldBe(mcuFwupData)
  }

  test("syncLatestFwupData clears FwupDataDao for NoUpdateNeeded error") {
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)
    fwupDataFetcher.fetchLatestFwupDataResult = Err(DownloadError(NoUpdateNeeded))

    service.syncLatestFwupData()

    fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
    fwupDataDaoProvider.fwupDataDaoMock.clearAllMcuFwupDataCalls.awaitItem()
    fwupDataDaoProvider.fwupDataDaoMock.clearCalls.awaitItem()
  }

  test("syncLatestFwupData doesn't do anything for other DownloadErrors") {
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)
    fwupDataFetcher.fetchLatestFwupDataResult =
      Err(DownloadError(FirmwareDownloadError.DownloadError(Throwable())))

    service.syncLatestFwupData()

    fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
    fwupDataDaoProvider.fwupDataDaoMock.clearAllMcuFwupDataCalls.expectNoEvents()
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
      firmwareUpdateSyncFrequency = FirmwareUpdateSyncFrequency(),
      hardwareProvisionedAppKeyStatusDao = hardwareProvisionedAppKeyStatusDao
    )

    serviceWithRealProvider.firmwareData().test {
      backgroundScope.launch {
        serviceWithRealProvider.executeWork()
      }

      // Initial state: real DAO
      awaitItem().shouldNotBeNull().apply {
        firmwareUpdateState.shouldBe(UpToDate)
      }

      // Set fake fwup data in the fake DAO only (using MCU data now)
      realFwupDataDaoFake.setMcuFwupData(listOf(McuFwupDataMock))
      realFwupDataDaoFake.setMcuFwupDataCalls.awaitItem()

      // Switch to fake hardware - should start using fake DAO with its data
      defaultAppConfigService.setIsHardwareFake(true)
      testCoroutineScheduler.runCurrent()

      // Should now emit with the fake DAO's data
      awaitItem().shouldNotBeNull().apply {
        val expectedMcuData = listOf(McuFwupDataMock).toImmutableList()
        firmwareUpdateState.shouldBe(PendingUpdate(mcuUpdates = expectedMcuData))
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
