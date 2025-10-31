package build.wallet.statemachine.account.create.full.onboard

import app.cash.turbine.plusAssign
import bitkey.recovery.DescriptorBackupError
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.NEW_ACCOUNT_DESCRIPTOR_BACKUP_LOADING
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.cloud.backup.csek.CsekFake
import build.wallet.cloud.backup.csek.SealedSsekFake
import build.wallet.cloud.backup.csek.SekGeneratorMock
import build.wallet.cloud.backup.csek.SsekDaoFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.onboarding.OnboardingKeyboxSealedSsekDaoFake
import build.wallet.recovery.DescriptorBackupServiceFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import okio.ByteString

class OnboardDescriptorBackupUiStateMachineImplTests : FunSpec({
  val descriptorBackupService = DescriptorBackupServiceFake()
  val sekGenerator = SekGeneratorMock()
  val ssekDao = SsekDaoFake()
  val onboardingKeyboxSealedSsekDao = OnboardingKeyboxSealedSsekDaoFake()

  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>("nfc") {}

  val stateMachine = OnboardDescriptorBackupUiStateMachineImpl(
    descriptorBackupService = descriptorBackupService,
    sekGenerator = sekGenerator,
    ssekDao = ssekDao,
    onboardingKeyboxSealedSsekDao = onboardingKeyboxSealedSsekDao,
    nfcSessionUIStateMachine = nfcSessionUIStateMachine
  )

  val onBackupCompleteCalls = turbines.create<Unit>("onBackupComplete")
  val onBackupFailedCalls = turbines.create<Throwable>("onBackupFailed")

  beforeTest {
    descriptorBackupService.reset()
    ssekDao.reset()
    onboardingKeyboxSealedSsekDao.reset()
    sekGenerator.csek = CsekFake
  }

  test("generates ssek, seals it, and uploads backup successfully") {
    val props = OnboardDescriptorBackupUiProps(
      fullAccount = FullAccountMock,
      sealedSsek = null,
      onBackupComplete = { onBackupCompleteCalls += Unit },
      onBackupFailed = { onBackupFailedCalls += it }
    )

    stateMachine.test(props) {
      // Shows loading while generating SSEK
      awaitBody<LoadingSuccessBodyModel> {
        id.shouldBe(NEW_ACCOUNT_DESCRIPTOR_BACKUP_LOADING)
      }

      // Delegates to NFC state machine for sealing
      awaitBodyMock<NfcSessionUIStateMachineProps<ByteString>>(id = nfcSessionUIStateMachine.id) {
        eventTrackerContext.shouldBe(NfcEventTrackerScreenIdContext.SEAL_SSEK)
        onSuccess(SealedSsekFake)
      }

      // Shows loading while uploading backup
      awaitBody<LoadingSuccessBodyModel> {
        id.shouldBe(NEW_ACCOUNT_DESCRIPTOR_BACKUP_LOADING)
      }

      // Verify completion callback
      onBackupCompleteCalls.awaitItem()

      // Verify SSEK was stored correctly
      ssekDao.get(SealedSsekFake).shouldBe(Ok(CsekFake))
      onboardingKeyboxSealedSsekDao.get().shouldBe(Ok(SealedSsekFake))
    }
  }

  test("uploads backup directly when sealed ssek provided") {
    val props = OnboardDescriptorBackupUiProps(
      fullAccount = FullAccountMock,
      sealedSsek = SealedSsekFake,
      onBackupComplete = { onBackupCompleteCalls += Unit },
      onBackupFailed = { onBackupFailedCalls += it }
    )

    stateMachine.test(props) {
      // Shows loading while uploading backup
      awaitBody<LoadingSuccessBodyModel> {
        id.shouldBe(NEW_ACCOUNT_DESCRIPTOR_BACKUP_LOADING)
      }

      // Verify completion callback
      onBackupCompleteCalls.awaitItem()
    }
  }

  test("calls onBackupFailed when nfc sealing is cancelled") {
    val props = OnboardDescriptorBackupUiProps(
      fullAccount = FullAccountMock,
      sealedSsek = null,
      onBackupComplete = { onBackupCompleteCalls += Unit },
      onBackupFailed = { onBackupFailedCalls += it }
    )

    stateMachine.test(props) {
      // Shows loading while generating SSEK
      awaitBody<LoadingSuccessBodyModel>()

      // User cancels NFC session
      awaitBodyMock<NfcSessionUIStateMachineProps<ByteString>>(id = nfcSessionUIStateMachine.id) {
        onCancel()
      }

      // Verify failure callback with cancellation error
      val error = onBackupFailedCalls.awaitItem()
      error.shouldBeTypeOf<Error>()
      error.message.shouldBe("User cancelled sealing SSEK NFC session")
    }
  }

  test("calls onBackupFailed when ssek dao storage fails") {
    ssekDao.setResult = Err(Error("Storage failed"))

    val props = OnboardDescriptorBackupUiProps(
      fullAccount = FullAccountMock,
      sealedSsek = null,
      onBackupComplete = { onBackupCompleteCalls += Unit },
      onBackupFailed = { onBackupFailedCalls += it }
    )

    stateMachine.test(props) {
      // Shows loading while generating SSEK
      awaitBody<LoadingSuccessBodyModel>()

      // Complete NFC sealing
      awaitBodyMock<NfcSessionUIStateMachineProps<ByteString>>(id = nfcSessionUIStateMachine.id) {
        onSuccess(SealedSsekFake)
      }

      // Verify failure callback
      val error = onBackupFailedCalls.awaitItem()
      error.shouldBeTypeOf<Error>()
      error.message.shouldBe("Storage failed")
    }
  }

  test("clears ssek dao when onboarding keybox sealed ssek dao storage fails") {
    onboardingKeyboxSealedSsekDao.shouldFailToStore = true

    val props = OnboardDescriptorBackupUiProps(
      fullAccount = FullAccountMock,
      sealedSsek = null,
      onBackupComplete = { onBackupCompleteCalls += Unit },
      onBackupFailed = { onBackupFailedCalls += it }
    )

    stateMachine.test(props) {
      // Shows loading while generating SSEK
      awaitBody<LoadingSuccessBodyModel>()

      // Complete NFC sealing
      awaitBodyMock<NfcSessionUIStateMachineProps<ByteString>>(id = nfcSessionUIStateMachine.id) {
        onSuccess(SealedSsekFake)
      }

      // Verify failure callback
      onBackupFailedCalls.awaitItem()

      // Verify SSEK dao was cleared
      ssekDao.get(SealedSsekFake).shouldBe(Ok(null))
    }
  }

  test("calls onBackupFailed when descriptor backup upload fails") {
    val uploadError = DescriptorBackupError.NetworkError(
      cause = Error("Server error")
    )
    descriptorBackupService.uploadOnboardingDescriptorBackupResult = Err(uploadError)

    val props = OnboardDescriptorBackupUiProps(
      fullAccount = FullAccountMock,
      sealedSsek = SealedSsekFake,
      onBackupComplete = { onBackupCompleteCalls += Unit },
      onBackupFailed = { onBackupFailedCalls += it }
    )

    stateMachine.test(props) {
      // Shows loading while uploading backup
      awaitBody<LoadingSuccessBodyModel>()

      // Verify failure callback
      val error = onBackupFailedCalls.awaitItem()
      error.shouldBeTypeOf<DescriptorBackupError.NetworkError>()
    }
  }
})
