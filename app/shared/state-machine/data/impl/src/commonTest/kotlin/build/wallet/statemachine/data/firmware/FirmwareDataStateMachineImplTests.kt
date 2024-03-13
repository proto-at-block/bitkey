package build.wallet.statemachine.data.firmware

import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.firmware.FirmwareDeviceInfoDaoMock
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.firmware.FirmwareDeviceNotFoundEnabledFeatureFlag
import build.wallet.fwup.FirmwareDownloadError
import build.wallet.fwup.FirmwareDownloadError.NoUpdateNeeded
import build.wallet.fwup.FwupDataDaoMock
import build.wallet.fwup.FwupDataFetcher.FwupDataFetcherError.DownloadError
import build.wallet.fwup.FwupDataFetcherMock
import build.wallet.fwup.FwupDataMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.firmware.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.statemachine.data.firmware.FirmwareData.FirmwareUpdateState.UpToDate
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class FirmwareDataStateMachineImplTests : FunSpec({

  val firmwareDeviceInfoDao =
    FirmwareDeviceInfoDaoMock(turbines::create)
  val fwupDataFetcher = FwupDataFetcherMock(turbines::create)
  val fwupDataDao = FwupDataDaoMock(turbines::create)
  val firmwareDeviceNotFoundEnabledFeatureFlag =
    FirmwareDeviceNotFoundEnabledFeatureFlag(featureFlagDao = FeatureFlagDaoMock())

  val stateMachine =
    FirmwareDataStateMachineImpl(
      firmwareDeviceNotFoundEnabledFeatureFlag = firmwareDeviceNotFoundEnabledFeatureFlag,
      firmwareDeviceInfoDao = firmwareDeviceInfoDao,
      fwupDataFetcher = fwupDataFetcher,
      fwupDataDao = fwupDataDao
    )

  val props = FirmwareDataProps(isHardwareFake = false)

  beforeTest {
    fwupDataDao.reset(testName = it.name.testName)
    fwupDataFetcher.reset(testName = it.name.testName)
  }

  test("FwupData returned from dao and cleared after onUpdateComplete") {
    val info = FirmwareDeviceInfoMock
    firmwareDeviceInfoDao.setDeviceInfo(info)

    stateMachine.test(props) {
      // firmware info initially null
      awaitItem().apply {
        firmwareUpdateState.shouldBe(UpToDate)
        firmwareDeviceInfo.shouldBe(null)
      }

      // firmwareinfo set to info
      awaitItem().apply {
        firmwareUpdateState.shouldBe(UpToDate)
        firmwareDeviceInfo.shouldBe(info)
      }
      fwupDataDao.fwupDataFlow.value = Ok(FwupDataMock)

      // calls to update firmware after info version differs
      fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
      fwupDataDao.setFwupDataCalls.awaitItem()

      val pendingUpdate = awaitItem().firmwareUpdateState.shouldBeTypeOf<PendingUpdate>()
      pendingUpdate.fwupData.shouldBe(FwupDataMock)
      pendingUpdate.onUpdateComplete()
      fwupDataDao.clearCalls.awaitItem()

      // device info updated with new version
      firmwareDeviceInfoDao.getDeviceInfo().get()
        .shouldBe(info.copy(version = "fake"))
    }
  }

  test("checkForNewFirmware stores new FwupData") {
    val info = FirmwareDeviceInfoMock
    firmwareDeviceInfoDao.setDeviceInfo(info)

    stateMachine.test(props) {
      // firmware info initially null
      awaitItem().apply {
        firmwareUpdateState.shouldBe(UpToDate)
        firmwareDeviceInfo.shouldBe(null)
      }

      // Initial call
      fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
      fwupDataDao.setFwupDataCalls.awaitItem()

      // Hourly call
      fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
      fwupDataDao.setFwupDataCalls.awaitItem()

      // Manual call
      val fwupData = FwupDataMock.copy(version = "manual")
      fwupDataFetcher.fetchLatestFwupDataResult = Ok(fwupData)
      awaitItem().checkForNewFirmware()

      with(fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()) {
        this.info.shouldBe(info)
      }

      fwupDataDao.setFwupDataCalls.awaitItem().shouldBe(fwupData)

      fwupDataFetcher.fetchLatestFwupDataCalls.cancelAndIgnoreRemainingEvents()
      fwupDataDao.setFwupDataCalls.cancelAndIgnoreRemainingEvents()
    }
  }

  test("checkForNewFirmware clears FwupDataDao for NoUpdateNeeded error") {
    fwupDataFetcher.fetchLatestFwupDataResult = Err(DownloadError(NoUpdateNeeded))

    stateMachine.test(props) {
      // firmware info initiated to null since its not set in repo
      awaitItem().apply {
        firmwareUpdateState.shouldBe(UpToDate)
        firmwareDeviceInfo.shouldBe(null)
      }

      // Initial call
      fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
      fwupDataDao.clearCalls.awaitItem()

      // Hourly call
      fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
      fwupDataDao.clearCalls.awaitItem()

      awaitItem().firmwareUpdateState.shouldBe(UpToDate)

      fwupDataFetcher.fetchLatestFwupDataCalls.cancelAndIgnoreRemainingEvents()
      fwupDataDao.clearCalls.cancelAndIgnoreRemainingEvents()
    }
  }

  test("checkForNewFirmware doesn't do anything for other DownloadErrors") {
    fwupDataFetcher.fetchLatestFwupDataResult =
      Err(
        DownloadError(FirmwareDownloadError.DownloadError(Throwable()))
      )

    stateMachine.test(props) {
      // firmware info initiated to null since its not set in repo
      awaitItem().apply {
        firmwareUpdateState.shouldBe(UpToDate)
        firmwareDeviceInfo.shouldBe(null)
      }

      // Initial call
      fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
      fwupDataDao.clearCalls.expectNoEvents()

      // Hourly call
      fwupDataFetcher.fetchLatestFwupDataCalls.awaitItem()
      fwupDataDao.clearCalls.expectNoEvents()

      awaitItem().firmwareUpdateState.shouldBe(UpToDate)

      fwupDataFetcher.fetchLatestFwupDataCalls.cancelAndIgnoreRemainingEvents()
    }
  }
})
