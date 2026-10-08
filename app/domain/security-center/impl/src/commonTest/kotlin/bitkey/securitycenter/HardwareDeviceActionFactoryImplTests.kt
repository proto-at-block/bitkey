package bitkey.securitycenter

import app.cash.turbine.test
import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.FullAccountW3Mock
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.fwup.FirmwareData
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.UpToDate
import build.wallet.fwup.FirmwareDataServiceFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly

class HardwareDeviceActionFactoryImplTests : FunSpec({
  val accountService = AccountServiceFake()
  val firmwareDataService = FirmwareDataServiceFake()
  val factory = HardwareDeviceActionFactoryImpl(firmwareDataService, accountService)

  beforeTest {
    accountService.reset()
    firmwareDataService.reset()
    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareUpdateState = UpToDate,
      firmwareDeviceInfo = FirmwareDeviceInfoMock.copy(hwRevision = "w3a-core-evt")
    )
  }

  test("W3 sentinel creates descriptor provisioning recommendation") {
    accountService.setActiveAccount(
      FullAccountW3Mock.copy(
        keybox = FullAccountW3Mock.keybox.copy(
          appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(
            AppGlobalAuthKeyHwSignature.W3_ONBOARDING_PLACEHOLDER
          )
        )
      )
    )

    factory.create().test {
      awaitItem().getRecommendations().shouldContainExactly(
        SecurityActionRecommendation.PROVISION_HARDWARE_DESCRIPTOR
      )
    }
  }

  test("W3 real signature has no descriptor provisioning recommendation") {
    accountService.setActiveAccount(FullAccountW3Mock)

    factory.create().test {
      awaitItem().getRecommendations().shouldBeEmpty()
    }
  }

  test("W1 ignores W3 sentinel") {
    accountService.setActiveAccount(
      FullAccountMock.copy(
        keybox = FullAccountMock.keybox.copy(
          appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(
            AppGlobalAuthKeyHwSignature.W3_ONBOARDING_PLACEHOLDER
          )
        )
      )
    )

    factory.create().test {
      awaitItem().getRecommendations().shouldBeEmpty()
    }
  }
})
