package bitkey.ui.screens.securityhub.education

import bitkey.securitycenter.HardwareDeviceAction
import bitkey.securitycenter.SecurityActionRecommendation
import bitkey.securitycenter.SocialRecoveryAction
import bitkey.ui.framework.test
import bitkey.ui.screens.securityhub.SecurityHubScreen
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.fwup.FirmwareDataPendingUpdateMock
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryDataMock
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementScreen
import build.wallet.statemachine.ui.awaitBody
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class SecurityHubEducationScreenPresenterTests : FunSpec({

  val presenter = SecurityHubEducationScreenPresenter()

  test("A valid action with education is presented") {
    presenter.test(
      screen = SecurityHubEducationScreen.ActionEducation(
        action = SocialRecoveryAction(
          relationships = null
        ),
        originScreen = SecurityHubScreen(
          account = FullAccountMock,
          hardwareRecoveryData = LostHardwareRecoveryDataMock
        ),
        firmwareData = FirmwareDataPendingUpdateMock.firmwareUpdateState
      )
    ) { navigator ->
      awaitBody<SecurityHubEducationBodyModel> {
        onContinue()
      }

      navigator.goToCalls.awaitItem().shouldBeTypeOf<TrustedContactManagementScreen>()
    }
  }

  test("A invalid action without education throws an error") {
    presenter.test(
      screen = SecurityHubEducationScreen.ActionEducation(
        action = HardwareDeviceAction(
          firmwareData = FirmwareDataPendingUpdateMock
        ),
        originScreen = SecurityHubScreen(
          account = FullAccountMock,
          hardwareRecoveryData = LostHardwareRecoveryDataMock
        ),
        firmwareData = FirmwareDataPendingUpdateMock.firmwareUpdateState
      )
    ) {
      awaitError().message.shouldBe("Unsupported action type: HARDWARE_DEVICE")
    }
  }

  test("onBack goes to the origin screen") {
    presenter.test(
      screen = SecurityHubEducationScreen.RecommendationEducation(
        recommendation = SecurityActionRecommendation.ADD_TRUSTED_CONTACTS,
        originScreen = SecurityHubScreen(
          account = FullAccountMock,
          hardwareRecoveryData = LostHardwareRecoveryDataMock
        ),
        firmwareData = FirmwareDataPendingUpdateMock.firmwareUpdateState
      )
    ) { navigator ->
      awaitBody<SecurityHubEducationBodyModel> {
        onBack()
      }

      navigator.goToCalls.awaitItem().shouldBeTypeOf<SecurityHubScreen>()
    }
  }
})
