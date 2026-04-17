package build.wallet.statemachine.account.full

import androidx.compose.runtime.Composable
import bitkey.ui.statemachine.interstitial.InterstitialUiProps
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.auth.FullAccountAuthKeyRotationServiceMock
import build.wallet.auth.PendingAuthKeyRotationAttempt
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.inappsecurity.BiometricAuthServiceFake
import build.wallet.recovery.Recovery
import build.wallet.recovery.Recovery.NoActiveRecovery
import build.wallet.recovery.RecoveryStatusServiceMock
import build.wallet.statemachine.BodyModelMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.app.InterstitialUiStateMachineFake
import build.wallet.statemachine.biometric.BiometricPromptProps
import build.wallet.statemachine.biometric.BiometricPromptUiStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.home.full.HomeUiProps
import build.wallet.statemachine.home.full.HomeUiStateMachine
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIOrigin
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIStateMachine
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIStateMachineProps
import build.wallet.statemachine.recovery.conflict.NoLongerRecoveringUiProps
import build.wallet.statemachine.recovery.conflict.NoLongerRecoveringUiStateMachine
import build.wallet.statemachine.recovery.conflict.SomeoneElseIsRecoveringUiProps
import build.wallet.statemachine.recovery.conflict.SomeoneElseIsRecoveringUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilBodyMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FullAccountUiStateMachineImplTests : FunSpec({

  val homeUiStateMachine =
    object : HomeUiStateMachine,
      ScreenStateMachineMock<HomeUiProps>(id = "home") {}

  val noLongerRecoveringUiStateMachine =
    object : NoLongerRecoveringUiStateMachine,
      ScreenStateMachineMock<NoLongerRecoveringUiProps>(id = "no-longer-recovering") {}

  val someoneElseIsRecoveringUiStateMachine =
    object : SomeoneElseIsRecoveringUiStateMachine,
      ScreenStateMachineMock<SomeoneElseIsRecoveringUiProps>(id = "someone-else-recovering") {}

  val authKeyRotationUiStateMachine =
    object : RotateAuthKeyUIStateMachine,
      ScreenStateMachineMock<RotateAuthKeyUIStateMachineProps>(id = "rotate-auth-key") {}

  val biometricAuthService = BiometricAuthServiceFake()

  val biometricPromptUiStateMachine = object : BiometricPromptUiStateMachine {
    @Composable
    override fun model(props: BiometricPromptProps): ScreenModel? {
      return if (props.shouldPromptForAuth) {
        BodyModelMock(
          id = "biometric-prompt",
          latestProps = props
        ).asRootScreen()
      } else {
        null
      }
    }
  }

  val interstitialUiStateMachine = InterstitialUiStateMachineFake()
  val fullAccountAuthKeyRotationService = FullAccountAuthKeyRotationServiceMock(turbines::create)
  val recoveryStatusService = RecoveryStatusServiceMock(
    recovery = NoActiveRecovery,
    turbines::create
  )

  val stateMachine = FullAccountUiStateMachineImpl(
    fullAccountAuthKeyRotationService = fullAccountAuthKeyRotationService,
    recoveryStatusService = recoveryStatusService,
    homeUiStateMachine = homeUiStateMachine,
    noLongerRecoveringUiStateMachine = noLongerRecoveringUiStateMachine,
    someoneElseIsRecoveringUiStateMachine = someoneElseIsRecoveringUiStateMachine,
    authKeyRotationUiStateMachine = authKeyRotationUiStateMachine,
    biometricAuthService = biometricAuthService,
    biometricPromptUiStateMachine = biometricPromptUiStateMachine,
    interstitialUiStateMachine = interstitialUiStateMachine
  )

  beforeTest {
    biometricAuthService.reset()
    interstitialUiStateMachine.reset()
    fullAccountAuthKeyRotationService.reset()
    recoveryStatusService.reset()
  }

  test("Loading screen shown when checking active account data") {
    recoveryStatusService.recoveryStatus.value = Recovery.Loading
    stateMachine.test(
      props = FullAccountUiProps(
        account = FullAccountMock
      )
    ) {
      awaitBody<LoadingSuccessBodyModel> {
        id.shouldBe(GeneralEventTrackerScreenId.LOADING_APP)
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }
  }

  test("Home screen shown for active account") {
    stateMachine.test(
      props = FullAccountUiProps(
        account = FullAccountMock,
        isNewlyCreatedAccount = false
      )
    ) {
      awaitBodyMock<HomeUiProps> {
        account.shouldBe(FullAccountMock)
      }
    }
  }

  test("Biometric prompt shown when auth required") {
    biometricAuthService.isBiometricAuthRequiredFlow.value = true

    stateMachine.test(
      props = FullAccountUiProps(
        account = FullAccountMock,
        isNewlyCreatedAccount = false
      )
    ) {
      awaitBodyMock<BiometricPromptProps> {
        shouldPromptForAuth.shouldBe(true)
      }
    }
  }

  test("Interstitial shown when coming from existing account and not onboarding") {
    biometricAuthService.isBiometricAuthRequiredFlow.value = false
    interstitialUiStateMachine.shouldShowInterstitial = true

    stateMachine.test(
      props = FullAccountUiProps(
        account = FullAccountMock,
        isNewlyCreatedAccount = false
      )
    ) {
      awaitBodyMock<InterstitialUiProps>(InterstitialUiStateMachineFake.BODY_MODEL_ID) {
        account.shouldBe(FullAccountMock)
        isComingFromOnboarding.shouldBe(false)
      }
    }
  }

  test("No interstitial shown when newly created account") {
    biometricAuthService.isBiometricAuthRequiredFlow.value = false
    interstitialUiStateMachine.shouldShowInterstitial = true

    stateMachine.test(
      props = FullAccountUiProps(
        account = FullAccountMock,
        isNewlyCreatedAccount = true
      )
    ) {
      awaitBodyMock<HomeUiProps> {
        account.shouldBe(FullAccountMock)
      }
    }
  }

  test("Auth key rotation screen shown for rotating auth keys") {
    stateMachine.test(
      props = FullAccountUiProps(
        account = FullAccountMock,
        isNewlyCreatedAccount = false
      )
    ) {
      awaitBodyMock<HomeUiProps>()
      fullAccountAuthKeyRotationService.pendingKeyRotationAttempt.value =
        PendingAuthKeyRotationAttempt.ProposedAttempt
      awaitUntilBodyMock<RotateAuthKeyUIStateMachineProps> {
        account.shouldBe(FullAccountMock)
        origin.shouldBe(RotateAuthKeyUIOrigin.PendingAttempt(PendingAuthKeyRotationAttempt.ProposedAttempt))
      }
    }
  }

  test("No longer recovering screen shown") {
    recoveryStatusService.recoveryStatus.value = Recovery.NoLongerRecovering(App)
    stateMachine.test(
      props = FullAccountUiProps(
        account = FullAccountMock
      )
    ) {
      awaitBodyMock<NoLongerRecoveringUiProps> {
        canceledRecoveryLostFactor.shouldBe(App)
      }
    }
  }

  test("Someone else is recovering screen shown") {
    recoveryStatusService.recoveryStatus.value = Recovery.SomeoneElseIsRecovering(App)
    stateMachine.test(
      props = FullAccountUiProps(
        account = FullAccountMock
      )
    ) {
      awaitBodyMock<SomeoneElseIsRecoveringUiProps> {
        fullAccountId.shouldBe(FullAccountIdMock)
        cancelingRecoveryLostFactor.shouldBe(App)
      }
    }
  }
})
