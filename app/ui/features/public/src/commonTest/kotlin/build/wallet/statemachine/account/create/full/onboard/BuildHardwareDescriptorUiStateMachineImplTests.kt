package build.wallet.statemachine.account.create.full.onboard

import app.cash.turbine.plusAssign
import bitkey.account.HardwareType
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.LOADING_ONBOARDING_STEP
import build.wallet.bitkey.keybox.FullAccountConfigMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.chaincode.delegation.ChaincodeExtractorFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.onboarding.CompleteOnboardingResponseV2
import build.wallet.f8e.onboarding.OnboardingF8eClientMock
import build.wallet.ktor.result.HttpError
import build.wallet.onboarding.OnboardingCompletionServiceFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BuildHardwareDescriptorUiStateMachineImplTests : FunSpec({
  val onboardingF8eClient = OnboardingF8eClientMock(turbines::create)
  val onboardingCompletionService = OnboardingCompletionServiceFake()
  val chaincodeExtractor = ChaincodeExtractorFake()

  val nfcSessionUIStateMachine = object :
    NfcSessionUIStateMachine,
    ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
      id = "nfc-session"
    ) {}

  val stateMachine = BuildHardwareDescriptorUiStateMachineImpl(
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    onboardingF8eClient = onboardingF8eClient,
    chaincodeExtractor = chaincodeExtractor,
    onboardingCompletionService = onboardingCompletionService
  )

  val onComplete = turbines.create<Unit>("onComplete")
  val onBackupFailed = turbines.create<Throwable>("onBackupFailed")

  val props = BuildHardwareDescriptorUiProps(
    fullAccount = FullAccountMock,
    onComplete = { onComplete += Unit },
    onBackupFailed = { onBackupFailed += it }
  )

  val mockResponse = CompleteOnboardingResponseV2(
    appAuthPub = "03a34b99f22c790c4e36b2b3c2c35a36db06226e41c692fc82b8b56ac1c540c5bd",
    hardwareAuthPub = "02b4632d08485ff1df2db55b9dafd23347d1c47a457072a1e87be26896549a8737",
    appSpendingPub = "03c4632d08485ff1df2db55b9dafd23347d1c47a457072a1e87be26896549a8737",
    hardwareSpendingPub = "03b4632d08485ff1df2db55b9dafd23347d1c47a457072a1e87be26896549a8737",
    serverSpendingPub = "02e3af28965693b9ce1228f9d468149b831d6a0540b25e8a9900f71372c11fb277",
    signature = "304402201234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef02201234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
  )

  beforeTest {
    onboardingF8eClient.reset()
    onboardingCompletionService.reset()
    chaincodeExtractor.reset()
  }

  test("shows loading screen and calls completeOnboardingV2") {
    onboardingF8eClient.completeOnboardingV2Result = Ok(mockResponse)

    stateMachine.test(props) {
      awaitUntilBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP) {
        message.shouldBe("Completing onboarding")
      }

      // Verify F8e client was called
      onboardingF8eClient.completeOnboardingV2Calls.awaitItem()

      // Should transition to intro screen after successful call
      awaitUntilScreenWithBody<PairNewHardwareBodyModel>()
    }
  }

  test("records fallback completion after successful completeOnboardingV2") {
    onboardingF8eClient.completeOnboardingV2Result = Ok(mockResponse)

    stateMachine.test(props) {
      awaitUntilBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      onboardingF8eClient.completeOnboardingV2Calls.awaitItem()

      // Verify fallback completion was recorded
      onboardingCompletionService.recordFallbackCompletionCalled.shouldBe(true)

      awaitUntilScreenWithBody<PairNewHardwareBodyModel>()
    }
  }

  test("calls onBackupFailed when completeOnboardingV2 fails") {
    val error = HttpError.NetworkError(RuntimeException("F8e error"))
    onboardingF8eClient.completeOnboardingV2Result = Err(error)

    stateMachine.test(props) {
      awaitUntilScreenWithBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      onboardingF8eClient.completeOnboardingV2Calls.awaitItem()

      // Should call onBackupFailed
      onBackupFailed.awaitItem().shouldBe(error)
    }
  }

  test("uses W3 keybox hardware type") {
    val w3Account = FullAccountMock.copy(
      keybox = KeyboxMock.copy(
        config = FullAccountConfigMock.copy(hardwareType = HardwareType.W3)
      )
    )
    val w3Props = props.copy(fullAccount = w3Account)

    onboardingF8eClient.completeOnboardingV2Result = Ok(mockResponse)

    stateMachine.test(w3Props) {
      awaitUntilBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      // Verify F8e client was called with W3 account
      onboardingF8eClient.completeOnboardingV2Calls.awaitItem()

      awaitBody<PairNewHardwareBodyModel>()
    }
  }
})
