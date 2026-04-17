package bitkey.ui.screens.securityhub.education

import bitkey.privilegedactions.FingerprintResetAvailabilityServiceImpl
import bitkey.securitycenter.HardwareDeviceAction
import bitkey.securitycenter.SecurityActionRecommendation
import bitkey.securitycenter.SocialRecoveryAction
import bitkey.ui.framework.test
import bitkey.ui.screens.securityhub.SecurityHubScreen
import build.wallet.availability.FunctionalityFeatureStates.FeatureState
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.FingerprintResetMinFirmwareVersionFeatureFlag
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.fwup.FirmwareData
import build.wallet.fwup.FirmwareDataPendingUpdateMock
import build.wallet.fwup.FirmwareDataServiceFake
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementScreen
import build.wallet.statemachine.settings.full.device.fingerprints.ManageFingerprintsOptionsSheet
import build.wallet.statemachine.ui.awaitBody
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class SecurityHubEducationScreenPresenterTests : FunSpec({

  val featureFlagDaoFake = FeatureFlagDaoFake()
  val firmwareDataServiceFake = FirmwareDataServiceFake()
  val presenter = SecurityHubEducationScreenPresenter(
    fingerprintResetAvailabilityService = FingerprintResetAvailabilityServiceImpl(
      fingerprintResetMinFirmwareVersionFeatureFlag = FingerprintResetMinFirmwareVersionFeatureFlag(featureFlagDaoFake),
      firmwareDataService = firmwareDataServiceFake
    ),
    firmwareDataService = firmwareDataServiceFake
  )

  test("A valid action with education is presented") {
    presenter.test(
      screen = SecurityHubEducationScreen.ActionEducation(
        action = SocialRecoveryAction(
          relationships = null,
          featureState = FeatureState.Available
        ),
        originScreen = SecurityHubScreen(
          account = FullAccountMock
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
          account = FullAccountMock
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
          account = FullAccountMock
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

  test("W3 hardware sets canEditFingerprints to false on fingerprint navigation") {
    // Set up W3 hardware
    firmwareDataServiceFake.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = FirmwareDeviceInfoMock.copy(hwRevision = "w3a-core-evt"),
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )

    presenter.test(
      screen = SecurityHubEducationScreen.RecommendationEducation(
        recommendation = SecurityActionRecommendation.ADD_FINGERPRINTS,
        originScreen = SecurityHubScreen(
          account = FullAccountMock
        ),
        firmwareData = FirmwareDataPendingUpdateMock.firmwareUpdateState
      )
    ) { navigator ->
      awaitBody<SecurityHubEducationBodyModel> {
        onContinue()
      }

      navigator.showSheetCalls.awaitItem().run {
        shouldBeTypeOf<ManageFingerprintsOptionsSheet>()
        canEditFingerprints.shouldBeFalse()
      }
    }
  }
})
