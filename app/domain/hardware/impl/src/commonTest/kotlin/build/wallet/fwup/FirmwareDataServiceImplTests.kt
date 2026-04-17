package build.wallet.fwup

import app.cash.turbine.test
import bitkey.account.AccountConfigServiceFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.firmware.FirmwareDeviceInfoDaoMock
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.firmware.McuInfo
import build.wallet.firmware.McuName
import build.wallet.firmware.FirmwareMetadata.FirmwareSlot
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
import kotlin.time.Duration.Companion.seconds

class FirmwareDataServiceImplTests : FunSpec({
  // TODO(W-10571): use real dispatcher.
  coroutineTestScope = true

  val firmwareDeviceInfoDao =
    FirmwareDeviceInfoDaoMock(turbines::create)
  val fwupDataFetcher = FwupDataFetcherMock(turbines::create)
  val fwupDataDao = FwupDataDaoMock(turbines::create)
  val hardwareProvisionedAppKeyStatusDao = HardwareProvisionedAppKeyStatusDaoFake()

  val appSessionManager = AppSessionManagerFake()
  val defaultAppConfigService = AccountConfigServiceFake()

  lateinit var service: FirmwareDataServiceImpl

  beforeTest {
    service = FirmwareDataServiceImpl(
      firmwareDeviceInfoDao = firmwareDeviceInfoDao,
      fwupDataFetcher = fwupDataFetcher,
      fwupDataDao = fwupDataDao,
      appSessionManager = appSessionManager,
      firmwareUpdateSyncFrequency = FirmwareUpdateSyncFrequency(defaultAppConfigService),
      hardwareProvisionedAppKeyStatusDao = hardwareProvisionedAppKeyStatusDao
    )
    firmwareDeviceInfoDao.reset()
    fwupDataDao.reset(testName = it.name.testName)
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
    fwupDataDao.setMcuFwupDataCalls.awaitItem()

    testCoroutineScheduler.advanceTimeBy(1.hours)
    // emit again after the polling duration
    fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
    fwupDataDao.setMcuFwupDataCalls.awaitItem()
  }

  test("changing sync frequency restarts the ticker with the new interval") {
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)
    backgroundScope.launch {
      service.executeWork()
    }

    // Initial sync fires immediately with the 1-hour ticker (real hardware)
    testCoroutineScheduler.runCurrent()
    fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
    fwupDataDao.setMcuFwupDataCalls.awaitItem()

    // Verify the ticker is on a 1-hour interval — no sync after only 5 seconds
    testCoroutineScheduler.advanceTimeBy(5.seconds)
    fwupDataFetcher.fetchLatestFwupDataCalls.expectNoEvents()

    // But does sync after a full hour
    testCoroutineScheduler.advanceTimeBy(1.hours - 5.seconds)
    fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
    fwupDataDao.setMcuFwupDataCalls.awaitItem()

    // Switch to fake hardware — frequency changes from 1 hour to 5 seconds
    defaultAppConfigService.setIsHardwareFake(true)

    // flatMapLatest restarts the ticker, which emits immediately
    testCoroutineScheduler.runCurrent()
    fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
    fwupDataDao.setMcuFwupDataCalls.awaitItem()

    // Verify the new 5-second interval is being used
    testCoroutineScheduler.advanceTimeBy(5.seconds)
    fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
    fwupDataDao.setMcuFwupDataCalls.awaitItem()
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
    fwupDataDao.setMcuFwupDataCalls.awaitItem()
  }

  test("changing deviceInfo triggers a sync") {
    val info = FirmwareDeviceInfoMock
    firmwareDeviceInfoDao.setDeviceInfo(info)

    backgroundScope.launch {
      service.executeWork()
    }

    testCoroutineScheduler.runCurrent()
    fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
    fwupDataDao.setMcuFwupDataCalls.awaitItem()

    firmwareDeviceInfoDao.setDeviceInfo(info.copy(version = "new-version"))
    fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
    fwupDataDao.setMcuFwupDataCalls.awaitItem()
  }

  test("updateFirmwareVersion updates device info and clears fwup") {
    // No device info clears the fwup dao
    service.updateFirmwareVersion(mcuUpdates = McuFwupDataListMock_W1)
    fwupDataDao.clearCalls.awaitItem()
    fwupDataDao.clearAllMcuStatesCalls.awaitItem()

    // With device info sets the new version and clears the fwup dao
    val info = FirmwareDeviceInfoMock
    firmwareDeviceInfoDao.setDeviceInfo(info)

    service.updateFirmwareVersion(mcuUpdates = McuFwupDataListMock_W1)

    // device info updated with new version and toggled active slot
    firmwareDeviceInfoDao.getDeviceInfo().get()
      .shouldBe(info.copy(version = "1.0.0-fake", activeSlot = FirmwareSlot.A))
    fwupDataDao.clearCalls.awaitItem()
    fwupDataDao.clearAllMcuStatesCalls.awaitItem()
  }

  test("updateFirmwareVersion with W3 multi-MCU updates both CORE and UXC versions") {
    // Set up W3 device info with existing MCU versions and per-MCU slots
    val w3DeviceInfo = FirmwareDeviceInfoMock.copy(
      version = "1.0.0",
      mcuInfo = listOf(
        McuInfo(mcuRole = McuRole.CORE, mcuName = McuName.EFR32, firmwareVersion = "1.0.0", activeSlot = FirmwareSlot.A),
        McuInfo(mcuRole = McuRole.UXC, mcuName = McuName.STM32U5, firmwareVersion = "1.0.0", activeSlot = FirmwareSlot.B)
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
    // Top-level active slot should toggle (B -> A)
    updatedInfo.activeSlot.shouldBe(FirmwareSlot.A)

    // mcuInfo should have updated versions and toggled slots for both MCUs
    updatedInfo.mcuInfo.size.shouldBe(2)
    val coreMcu = updatedInfo.mcuInfo.find { it.mcuRole == McuRole.CORE }
    coreMcu?.firmwareVersion.shouldBe(McuFwupDataMock_W3_CORE.version)
    coreMcu?.activeSlot.shouldBe(FirmwareSlot.B) // Toggled from A
    val uxcMcu = updatedInfo.mcuInfo.find { it.mcuRole == McuRole.UXC }
    uxcMcu?.firmwareVersion.shouldBe(McuFwupDataMock_W3_UXC.version)
    uxcMcu?.activeSlot.shouldBe(FirmwareSlot.A) // Toggled from B

    // DAO should be cleared
    fwupDataDao.clearCalls.awaitItem()
    fwupDataDao.clearAllMcuStatesCalls.awaitItem()
  }

  test("updateFirmwareVersion with partial MCU update only updates provided MCUs") {
    // Set up W3 device info
    val w3DeviceInfo = FirmwareDeviceInfoMock.copy(
      version = "1.0.0",
      mcuInfo = listOf(
        McuInfo(mcuRole = McuRole.CORE, mcuName = McuName.EFR32, firmwareVersion = "1.0.0", activeSlot = FirmwareSlot.A),
        McuInfo(mcuRole = McuRole.UXC, mcuName = McuName.STM32U5, firmwareVersion = "1.0.0", activeSlot = FirmwareSlot.A)
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
    // Top-level slot toggled (B -> A)
    updatedInfo.activeSlot.shouldBe(FirmwareSlot.A)
    // CORE was updated — version and slot toggled
    updatedInfo.mcuInfo.find { it.mcuRole == McuRole.CORE }?.firmwareVersion
      .shouldBe(McuFwupDataMock_W3_CORE.version)
    updatedInfo.mcuInfo.find { it.mcuRole == McuRole.CORE }?.activeSlot
      .shouldBe(FirmwareSlot.B) // Toggled from A
    // UXC was NOT updated — version and slot unchanged
    updatedInfo.mcuInfo.find { it.mcuRole == McuRole.UXC }?.firmwareVersion
      .shouldBe("1.0.0")
    updatedInfo.mcuInfo.find { it.mcuRole == McuRole.UXC }?.activeSlot
      .shouldBe(FirmwareSlot.A) // Unchanged

    fwupDataDao.clearCalls.awaitItem()
    fwupDataDao.clearAllMcuStatesCalls.awaitItem()
  }

  test("updateFirmwareVersion with UXC-only update does not toggle top-level slot") {
    // Top-level activeSlot represents CORE's slot, so a UXC-only update should not toggle it
    val w3DeviceInfo = FirmwareDeviceInfoMock.copy(
      version = "1.0.0",
      mcuInfo = listOf(
        McuInfo(mcuRole = McuRole.CORE, mcuName = McuName.EFR32, firmwareVersion = "1.0.0", activeSlot = FirmwareSlot.A),
        McuInfo(mcuRole = McuRole.UXC, mcuName = McuName.STM32U5, firmwareVersion = "1.0.0", activeSlot = FirmwareSlot.A)
      )
    )
    firmwareDeviceInfoDao.setDeviceInfo(w3DeviceInfo)

    val uxcOnlyUpdate = listOf(McuFwupDataMock_W3_UXC).toImmutableList()
    service.updateFirmwareVersion(mcuUpdates = uxcOnlyUpdate)

    val updatedInfo = firmwareDeviceInfoDao.getDeviceInfo().get()
    updatedInfo.shouldNotBeNull()

    // Version stays the same (no CORE update, so fallback to existing)
    updatedInfo.version.shouldBe("1.0.0")
    // Top-level slot unchanged — it tracks CORE, not UXC
    updatedInfo.activeSlot.shouldBe(FirmwareSlot.B) // FirmwareDeviceInfoMock default
    // UXC per-MCU slot toggled
    updatedInfo.mcuInfo.find { it.mcuRole == McuRole.UXC }?.activeSlot
      .shouldBe(FirmwareSlot.B) // Toggled from A
    // CORE unchanged
    updatedInfo.mcuInfo.find { it.mcuRole == McuRole.CORE }?.activeSlot
      .shouldBe(FirmwareSlot.A)

    fwupDataDao.clearCalls.awaitItem()
    fwupDataDao.clearAllMcuStatesCalls.awaitItem()
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

      // Intermediate emission: deviceInfo updated but mcuFwupData not yet
      awaitItem().shouldNotBeNull().apply {
        firmwareUpdateState.shouldBe(UpToDate)
        firmwareDeviceInfo.shouldBe(info)
      }

      // Capture the periodic sync work
      fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
      fwupDataDao.setMcuFwupDataCalls.awaitItem()

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
    fwupDataDao.setMcuFwupDataCalls.awaitItem().shouldBe(mcuFwupData)
  }

  test("syncLatestFwupData clears FwupDataDao for NoUpdateNeeded error") {
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)
    fwupDataFetcher.fetchLatestFwupDataResult = Err(DownloadError(NoUpdateNeeded))

    service.syncLatestFwupData()

    fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
    fwupDataDao.clearAllMcuFwupDataCalls.awaitItem()
    fwupDataDao.clearCalls.awaitItem()
  }

  test("syncLatestFwupData doesn't do anything for other DownloadErrors") {
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)
    fwupDataFetcher.fetchLatestFwupDataResult =
      Err(DownloadError(FirmwareDownloadError.DownloadError(Throwable())))

    service.syncLatestFwupData()

    fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
    fwupDataDao.clearAllMcuFwupDataCalls.expectNoEvents()
  }
})
