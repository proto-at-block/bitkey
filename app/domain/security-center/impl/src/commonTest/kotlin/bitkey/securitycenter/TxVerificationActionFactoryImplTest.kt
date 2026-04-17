package bitkey.securitycenter

import app.cash.turbine.test
import bitkey.verification.TxVerificationServiceFake
import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.TxVerificationFeatureFlag
import build.wallet.firmware.FirmwareDeviceInfoDaoMock
import build.wallet.firmware.FirmwareDeviceInfoMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull

class TxVerificationActionFactoryImplTest : FunSpec({
  val featureFlagDao = FeatureFlagDaoFake()
  val txVerificationFlag = TxVerificationFeatureFlag(featureFlagDao)
  val txVerificationService = TxVerificationServiceFake()
  val appFunctionalityService = AppFunctionalityServiceFake()
  val firmwareDeviceInfoDao = FirmwareDeviceInfoDaoMock(turbines::create)

  val factory = TxVerificationActionFactoryImpl(
    flag = txVerificationFlag,
    txVerificationService = txVerificationService,
    appFunctionalityService = appFunctionalityService,
    firmwareDeviceInfoDao = firmwareDeviceInfoDao
  )

  beforeTest {
    featureFlagDao.reset()
    txVerificationService.reset()
    firmwareDeviceInfoDao.reset()
    txVerificationFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)
  }

  test("returns null when flag is disabled") {
    txVerificationFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))

    factory.create().test {
      awaitItem().shouldBeNull()
    }
  }

  test("returns action when flag is enabled and hardware is W1") {
    firmwareDeviceInfoDao.setDeviceInfo(
      FirmwareDeviceInfoMock.copy(hwRevision = "w1a-dvt")
    )

    factory.create().test {
      awaitItem().shouldNotBeNull()
    }
  }

  test("returns null when hardware is W3") {
    firmwareDeviceInfoDao.setDeviceInfo(
      FirmwareDeviceInfoMock.copy(hwRevision = "w3a-core-evt")
    )

    factory.create().test {
      awaitItem().shouldBeNull()
    }
  }

  test("returns action when no device info is available") {
    firmwareDeviceInfoDao.reset()

    factory.create().test {
      awaitItem().shouldNotBeNull()
    }
  }
})
