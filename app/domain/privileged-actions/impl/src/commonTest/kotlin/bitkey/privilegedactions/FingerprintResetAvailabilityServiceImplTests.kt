package bitkey.privilegedactions

import app.cash.turbine.test
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.FingerprintResetMinFirmwareVersionFeatureFlag
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareMetadata
import build.wallet.fwup.FirmwareData
import build.wallet.fwup.FirmwareDataServiceFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class FingerprintResetAvailabilityServiceImplTests : FunSpec({

  val featureFlagDao = FeatureFlagDaoFake()
  val fingerprintResetMinFirmwareVersionFeatureFlag = FingerprintResetMinFirmwareVersionFeatureFlag(featureFlagDao)
  val firmwareDataService = FirmwareDataServiceFake()

  val availability = FingerprintResetAvailabilityServiceImpl(
    fingerprintResetMinFirmwareVersionFeatureFlag = fingerprintResetMinFirmwareVersionFeatureFlag,
    firmwareDataService = firmwareDataService
  )

  beforeTest {
    featureFlagDao.reset()
    firmwareDataService.reset()
  }

  test("isAvailable returns true when firmware version meets minimum requirement") {
    fingerprintResetMinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag("1.0.98"))

    // Test with version equal to minimum
    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = createFirmwareDeviceInfo("1.0.98"),
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )

    availability.isAvailable().test {
      awaitItem() shouldBe true
    }

    // Test with version greater than minimum
    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = createFirmwareDeviceInfo("2.1.0"),
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )

    availability.isAvailable().test {
      awaitItem() shouldBe true
    }
  }

  test("isAvailable returns false when firmware version below minimum requirement") {
    fingerprintResetMinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag("1.0.98"))

    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = createFirmwareDeviceInfo("1.0.97"),
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )

    availability.isAvailable().test {
      awaitItem() shouldBe false
    }
  }

  test("isAvailable returns false when no firmware device info") {
    fingerprintResetMinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag("1.0.98"))

    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = null,
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )

    availability.isAvailable().test {
      awaitItem() shouldBe false
    }
  }

  test("isAvailable returns false when minimum version flag is empty") {
    fingerprintResetMinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag(""))

    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = createFirmwareDeviceInfo("2.1.0"),
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )

    availability.isAvailable().test {
      awaitItem() shouldBe false
    }
  }

  test("isAvailable reacts to firmware version changes") {
    fingerprintResetMinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag("1.0.98"))

    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = createFirmwareDeviceInfo("1.0.97"),
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )

    availability.isAvailable().test {
      awaitItem() shouldBe false

      firmwareDataService.firmwareData.value = FirmwareData(
        firmwareDeviceInfo = createFirmwareDeviceInfo("1.0.98"),
        firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
      )
      awaitItem() shouldBe true
    }
  }

  test("isAvailable reacts to minimum firmware version flag changes") {
    fingerprintResetMinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag("3.0.0"))

    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = createFirmwareDeviceInfo("2.5.0"),
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )

    availability.isAvailable().test {
      awaitItem() shouldBe false

      fingerprintResetMinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag("1.0.98"))
      awaitItem() shouldBe true
    }
  }

  test("isAvailable returns false when firmware feature flag is an empty string") {
    fingerprintResetMinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag(""))

    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = createFirmwareDeviceInfo("1.0.98"),
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )

    availability.isAvailable().test {
      awaitItem() shouldBe false
    }
  }
})

private fun createFirmwareDeviceInfo(version: String) =
  FirmwareDeviceInfo(
    version = version,
    serial = "test-serial",
    swType = "test",
    hwRevision = "test",
    activeSlot = FirmwareMetadata.FirmwareSlot.A,
    batteryCharge = 50.0,
    vCell = 1000,
    avgCurrentMa = 100,
    batteryCycles = 10,
    secureBootConfig = build.wallet.firmware.SecureBootConfig.DEV,
    timeRetrieved = Instant.fromEpochSeconds(1234567890).epochSeconds,
    bioMatchStats = null,
    mcuInfo = emptyList()
  )
