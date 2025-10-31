package build.wallet.statemachine.account.create.full

import bitkey.onboarding.DeleteFullAccountServiceMock
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.DELETING_FULL_ACCOUNT
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.FAILURE_DELETING_FULL_ACCOUNT
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.onboarding.OnboardingKeyboxStep.DescriptorBackup
import build.wallet.onboarding.OnboardingKeyboxStepState.Complete
import build.wallet.onboarding.OnboardingKeyboxStepState.Incomplete
import build.wallet.onboarding.OnboardingKeyboxStepStateDaoFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request.HwKeyProof
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.robots.awaitLoadingScreen
import build.wallet.ui.model.alert.ButtonAlertModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.flow.first

class OverwriteFullAccountCloudBackupUiStateMachineImplTests : FunSpec({

  val deleteFullAccountService = DeleteFullAccountServiceMock(turbines::create)
  val onboardingKeyboxStepStateDao = OnboardingKeyboxStepStateDaoFake()

  val rollbackCalls = turbines.create<Unit>("rollback calls")
  val onOverwriteCalls = turbines.create<Unit>("overwrite calls")

  val props = OverwriteFullAccountCloudBackupUiProps(
    keybox = KeyboxMock,
    onOverwrite = { onOverwriteCalls.add(Unit) },
    rollback = { rollbackCalls.add(Unit) }
  )

  val overwriteFullAccountCloudBackupUiStateMachine =
    OverwriteFullAccountCloudBackupUiStateMachineImpl(
      deleteFullAccountService = deleteFullAccountService,
      proofOfPossessionNfcStateMachine =
        object : ProofOfPossessionNfcStateMachine,
          ScreenStateMachineMock<ProofOfPossessionNfcProps>(
            id = "pop-nfc"
          ) {},
      onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao
    )

  beforeTest {
    deleteFullAccountService.reset()
    onboardingKeyboxStepStateDao.clear()
  }

  test("cancel") {
    onboardingKeyboxStepStateDao.setStateForStep(DescriptorBackup, Complete)
    overwriteFullAccountCloudBackupUiStateMachine.test(props = props) {
      awaitBody<OverwriteFullAccountCloudBackupWarningModel> {
        onCancel()
      }
      awaitBodyMock<ProofOfPossessionNfcProps> {
        request.shouldBeTypeOf<HwKeyProof>().onSuccess(HwFactorProofOfPossession("fake"))
      }
      awaitLoadingScreen(DELETING_FULL_ACCOUNT)
      deleteFullAccountService.deleteAccountCalls.awaitItem()
      onboardingKeyboxStepStateDao.stateForStep(DescriptorBackup).first().shouldBe(Incomplete)
      rollbackCalls.awaitItem()
    }
  }

  test("cancel - pop on back") {
    overwriteFullAccountCloudBackupUiStateMachine.test(props = props) {
      awaitBody<OverwriteFullAccountCloudBackupWarningModel> {
        onCancel()
      }
      awaitBodyMock<ProofOfPossessionNfcProps> {
        onBack()
      }
      awaitBody<OverwriteFullAccountCloudBackupWarningModel>()
    }
  }

  test("cancel - delete account error - retry") {
    overwriteFullAccountCloudBackupUiStateMachine.test(props = props) {
      deleteFullAccountService.returnError = Error("foo")
      awaitBody<OverwriteFullAccountCloudBackupWarningModel> {
        onCancel()
      }
      awaitBodyMock<ProofOfPossessionNfcProps> {
        request.shouldBeTypeOf<HwKeyProof>().onSuccess(HwFactorProofOfPossession("fake"))
      }
      awaitLoadingScreen(DELETING_FULL_ACCOUNT)
      awaitBody<FormBodyModel>(FAILURE_DELETING_FULL_ACCOUNT) {
        clickPrimaryButton()
      }
      deleteFullAccountService.deleteAccountCalls.awaitItem()
      awaitBodyMock<ProofOfPossessionNfcProps>()
    }
  }

  test("cancel - delete account error - back") {
    overwriteFullAccountCloudBackupUiStateMachine.test(props = props) {
      deleteFullAccountService.returnError = Error("foo")
      awaitBody<OverwriteFullAccountCloudBackupWarningModel> {
        onCancel()
      }
      awaitBodyMock<ProofOfPossessionNfcProps> {
        request.shouldBeTypeOf<HwKeyProof>().onSuccess(HwFactorProofOfPossession("fake"))
      }
      awaitLoadingScreen(DELETING_FULL_ACCOUNT)
      awaitBody<FormBodyModel>(FAILURE_DELETING_FULL_ACCOUNT) {
        onBack.shouldNotBeNull().invoke()
      }
      deleteFullAccountService.deleteAccountCalls.awaitItem()
      awaitBody<OverwriteFullAccountCloudBackupWarningModel>()
    }
  }

  test("overwrite") {
    overwriteFullAccountCloudBackupUiStateMachine.test(props = props) {
      awaitBody<OverwriteFullAccountCloudBackupWarningModel> {
        onOverwriteExistingBackup()
      }
      awaitItem().alertModel.shouldBeTypeOf<ButtonAlertModel>().onPrimaryButtonClick()
      onOverwriteCalls.awaitItem()
    }
  }

  test("overwrite - cancel dialog") {
    overwriteFullAccountCloudBackupUiStateMachine.test(props = props) {
      awaitBody<OverwriteFullAccountCloudBackupWarningModel> {
        onOverwriteExistingBackup()
      }
      awaitItem().alertModel.shouldBeTypeOf<ButtonAlertModel>().onSecondaryButtonClick?.invoke()
      awaitItem().alertModel.shouldBeNull()
      onOverwriteCalls.expectNoEvents()
    }
  }
})
