package build.wallet.statemachine.account.create.full

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.DELETING_FULL_ACCOUNT
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.FAILURE_DELETING_FULL_ACCOUNT
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.OVERWRITE_FULL_ACCOUNT_CLOUD_BACKUP_WARNING_DURING_ONBOARDING
import build.wallet.auth.OnboardingFullAccountDeleterMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request.HwKeyProof
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.account.CreateFullAccountData.OverwriteFullAccountCloudBackupData
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.clickSecondaryButton
import build.wallet.statemachine.ui.robots.awaitLoadingScreen
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeTypeOf

class OverwriteFullAccountCloudBackupUiStateMachineImplTests : FunSpec({

  val onboardingFullAccountDeleter = OnboardingFullAccountDeleterMock(turbines::create)

  val rollbackCalls = turbines.create<Unit>("rollback calls")
  val onOverwriteCalls = turbines.create<Unit>("overwrite calls")

  val props =
    OverwriteFullAccountCloudBackupUiProps(
      data =
        OverwriteFullAccountCloudBackupData(
          keybox = KeyboxMock,
          onOverwrite = { onOverwriteCalls.add(Unit) },
          rollback = { rollbackCalls.add(Unit) }
        )
    )

  val overwriteFullAccountCloudBackupUiStateMachine =
    OverwriteFullAccountCloudBackupUiStateMachineImpl(
      onboardingFullAccountDeleter = onboardingFullAccountDeleter,
      proofOfPossessionNfcStateMachine =
        object : ProofOfPossessionNfcStateMachine, ScreenStateMachineMock<ProofOfPossessionNfcProps>(
          id = "pop-nfc"
        ) {}
    )

  beforeTest {
    onboardingFullAccountDeleter.reset()
  }

  test("cancel") {
    overwriteFullAccountCloudBackupUiStateMachine.test(props = props) {
      awaitScreenWithBody<FormBodyModel>(OVERWRITE_FULL_ACCOUNT_CLOUD_BACKUP_WARNING_DURING_ONBOARDING) {
        clickSecondaryButton()
      }
      awaitScreenWithBodyModelMock<ProofOfPossessionNfcProps> {
        request.shouldBeTypeOf<HwKeyProof>().onSuccess(HwFactorProofOfPossession("fake"))
      }
      awaitLoadingScreen(DELETING_FULL_ACCOUNT)
      onboardingFullAccountDeleter.deleteAccountCalls.awaitItem()
      rollbackCalls.awaitItem()
    }
  }

  test("cancel - pop on back") {
    overwriteFullAccountCloudBackupUiStateMachine.test(props = props) {
      awaitScreenWithBody<FormBodyModel>(OVERWRITE_FULL_ACCOUNT_CLOUD_BACKUP_WARNING_DURING_ONBOARDING) {
        clickSecondaryButton()
      }
      awaitScreenWithBodyModelMock<ProofOfPossessionNfcProps> {
        onBack()
      }
      awaitScreenWithBody<FormBodyModel>(OVERWRITE_FULL_ACCOUNT_CLOUD_BACKUP_WARNING_DURING_ONBOARDING)
    }
  }

  test("cancel - delete account error - retry") {
    overwriteFullAccountCloudBackupUiStateMachine.test(props = props) {
      onboardingFullAccountDeleter.returnError = Error("foo")
      awaitScreenWithBody<FormBodyModel>(OVERWRITE_FULL_ACCOUNT_CLOUD_BACKUP_WARNING_DURING_ONBOARDING) {
        clickSecondaryButton()
      }
      awaitScreenWithBodyModelMock<ProofOfPossessionNfcProps> {
        request.shouldBeTypeOf<HwKeyProof>().onSuccess(HwFactorProofOfPossession("fake"))
      }
      awaitLoadingScreen(DELETING_FULL_ACCOUNT)
      awaitScreenWithBody<FormBodyModel>(FAILURE_DELETING_FULL_ACCOUNT) {
        clickPrimaryButton()
      }
      onboardingFullAccountDeleter.deleteAccountCalls.awaitItem()
      awaitScreenWithBodyModelMock<ProofOfPossessionNfcProps>()
    }
  }

  test("cancel - delete account error - back") {
    overwriteFullAccountCloudBackupUiStateMachine.test(props = props) {
      onboardingFullAccountDeleter.returnError = Error("foo")
      awaitScreenWithBody<FormBodyModel>(OVERWRITE_FULL_ACCOUNT_CLOUD_BACKUP_WARNING_DURING_ONBOARDING) {
        clickSecondaryButton()
      }
      awaitScreenWithBodyModelMock<ProofOfPossessionNfcProps> {
        request.shouldBeTypeOf<HwKeyProof>().onSuccess(HwFactorProofOfPossession("fake"))
      }
      awaitLoadingScreen(DELETING_FULL_ACCOUNT)
      awaitScreenWithBody<FormBodyModel>(FAILURE_DELETING_FULL_ACCOUNT) {
        onBack.shouldNotBeNull().invoke()
      }
      onboardingFullAccountDeleter.deleteAccountCalls.awaitItem()
      awaitScreenWithBody<FormBodyModel>(OVERWRITE_FULL_ACCOUNT_CLOUD_BACKUP_WARNING_DURING_ONBOARDING)
    }
  }

  test("overwrite") {
    overwriteFullAccountCloudBackupUiStateMachine.test(props = props) {
      awaitScreenWithBody<FormBodyModel>(OVERWRITE_FULL_ACCOUNT_CLOUD_BACKUP_WARNING_DURING_ONBOARDING) {
        clickPrimaryButton()
      }
      awaitItem().alertModel.shouldNotBeNull().onPrimaryButtonClick()
      onOverwriteCalls.awaitItem()
    }
  }

  test("overwrite - cancel dialog") {
    overwriteFullAccountCloudBackupUiStateMachine.test(props = props) {
      awaitScreenWithBody<FormBodyModel>(OVERWRITE_FULL_ACCOUNT_CLOUD_BACKUP_WARNING_DURING_ONBOARDING) {
        clickPrimaryButton()
      }
      awaitItem().alertModel.shouldNotBeNull().onSecondaryButtonClick?.invoke()
      awaitItem().alertModel.shouldBeNull()
      onOverwriteCalls.expectNoEvents()
    }
  }
})
