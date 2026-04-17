package build.wallet.statemachine.nfc

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.LOADING_ONBOARDING_STEP
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.keybox.PrivateAccountMock
import build.wallet.cloud.backup.health.CloudBackupHealthRepositoryMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.onboarding.HardwareDescriptorDeliveryServiceFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.clickSecondaryButton
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class DescriptorRepairUiStateMachineImplTests : FunSpec({
  val hardwareDescriptorDeliveryService = HardwareDescriptorDeliveryServiceFake()
  val keyboxDao = KeyboxDaoMock(turbines::create)
  val cloudBackupHealthRepository = CloudBackupHealthRepositoryMock(turbines::create)

  val nfcSessionUIStateMachine = object :
    NfcSessionUIStateMachine,
    ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
      id = "nfc-session"
    ) {}

  val stateMachine = DescriptorRepairUiStateMachineImpl(
    hardwareDescriptorDeliveryService = hardwareDescriptorDeliveryService,
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    keyboxDao = keyboxDao,
    cloudBackupHealthRepository = cloudBackupHealthRepository,
  )

  val onBack = turbines.create<Unit>("onBack")
  val onRepairComplete = turbines.create<Unit>("onRepairComplete")

  val props = DescriptorRepairUiProps(
    fullAccount = PrivateAccountMock,
    presentationStyle = ScreenPresentationStyle.FullScreen,
    onRepairComplete = { onRepairComplete += Unit },
    onBack = { onBack += Unit },
  )

  beforeTest {
    hardwareDescriptorDeliveryService.reset()
    keyboxDao.reset()
    cloudBackupHealthRepository.reset()
  }

  test("shows loading then transitions to NFC session on successful server call") {
    hardwareDescriptorDeliveryService.fetchSignatureAndPrepareNfcSessionResult = Ok { _, _ -> "fake-hw-signature" }

    stateMachine.test(props) {
      // Should show loading screen while fetching signature from server
      awaitUntilBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      // Should transition to NFC session after server call succeeds
      awaitBodyMock<NfcSessionUIStateMachineProps<String>>(id = "nfc-session")
    }
  }

  test("calls onRepairComplete when NFC session succeeds") {
    hardwareDescriptorDeliveryService.fetchSignatureAndPrepareNfcSessionResult = Ok { _, _ -> "fake-hw-signature" }

    stateMachine.test(props) {
      awaitUntilBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      awaitBodyMock<NfcSessionUIStateMachineProps<String>>(id = "nfc-session") {
        onSuccess("fake-hw-signature")
      }

      keyboxDao.activeKeybox.value.get()?.appGlobalAuthKeyHwSignature.shouldBe(
        AppGlobalAuthKeyHwSignature("fake-hw-signature")
      )
      cloudBackupHealthRepository.performSyncCalls.awaitItem()
      onRepairComplete.awaitItem()
    }
  }

  test("calls onBack when NFC session is cancelled") {
    hardwareDescriptorDeliveryService.fetchSignatureAndPrepareNfcSessionResult = Ok { _, _ -> "fake-hw-signature" }

    stateMachine.test(props) {
      awaitUntilBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      awaitBodyMock<NfcSessionUIStateMachineProps<String>>(id = "nfc-session") {
        onCancel()
      }

      onBack.awaitItem()
    }
  }

  test("shows error screen when server call fails") {
    hardwareDescriptorDeliveryService.fetchSignatureAndPrepareNfcSessionResult = Err(Error("Network error"))

    stateMachine.test(props) {
      awaitUntilBody<FormBodyModel>(id = LOADING_ONBOARDING_STEP) {
        header.shouldNotBeNull().headline.shouldBe("Wallet preparation failed")
        primaryButton.shouldNotBeNull().text.shouldBe("Retry")
      }
    }
  }

  test("retry on error screen re-attempts server call") {
    hardwareDescriptorDeliveryService.fetchSignatureAndPrepareNfcSessionResult = Err(Error("Network error"))

    stateMachine.test(props) {
      // First attempt fails — error screen shown
      awaitUntilBody<FormBodyModel>(id = LOADING_ONBOARDING_STEP) {
        // Fix the service before clicking retry
        hardwareDescriptorDeliveryService.fetchSignatureAndPrepareNfcSessionResult = Ok { _, _ -> "fake-hw-signature" }
        clickPrimaryButton()
      }

      // Should show loading again
      awaitUntilBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      // Should transition to NFC session after retry succeeds
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session")
    }
  }

  test("go back on error screen calls onBack") {
    hardwareDescriptorDeliveryService.fetchSignatureAndPrepareNfcSessionResult = Err(Error("Network error"))

    stateMachine.test(props) {
      awaitUntilBody<FormBodyModel>(id = LOADING_ONBOARDING_STEP) {
        clickSecondaryButton()
      }

      onBack.awaitItem()
    }
  }
})
