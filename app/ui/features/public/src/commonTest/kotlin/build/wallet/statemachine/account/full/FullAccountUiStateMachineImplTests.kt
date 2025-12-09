package build.wallet.statemachine.account.full

import androidx.compose.runtime.Composable
import bitkey.ui.statemachine.interstitial.InterstitialUiProps
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.auth.PendingAuthKeyRotationAttempt
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.inappsecurity.BiometricAuthServiceFake
import build.wallet.statemachine.BodyModelMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.app.InterstitialUiStateMachineFake
import build.wallet.statemachine.biometric.BiometricPromptProps
import build.wallet.statemachine.biometric.BiometricPromptUiStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.AccountData
import build.wallet.statemachine.data.keybox.AccountData.CheckingActiveAccountData
import build.wallet.statemachine.data.keybox.ActiveKeyboxLoadedDataMock
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData
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

  val stateMachine = FullAccountUiStateMachineImpl(
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
  }

  test("Loading screen shown when checking active account data") {
    stateMachine.test(
      props = FullAccountUiProps(
        accountData = CheckingActiveAccountData
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
        accountData = ActiveKeyboxLoadedDataMock,
        isNewlyCreatedAccount = false,
        isRenderingViaAccountData = false
      )
    ) {
      awaitBodyMock<HomeUiProps> {
        account.shouldBe(ActiveKeyboxLoadedDataMock.account)
      }
    }
  }

  test("Biometric prompt shown when auth required") {
    biometricAuthService.isBiometricAuthRequiredFlow.value = true

    stateMachine.test(
      props = FullAccountUiProps(
        accountData = ActiveKeyboxLoadedDataMock,
        isNewlyCreatedAccount = false,
        isRenderingViaAccountData = false
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
        accountData = ActiveKeyboxLoadedDataMock,
        isNewlyCreatedAccount = false,
        isRenderingViaAccountData = false
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
        accountData = ActiveKeyboxLoadedDataMock,
        isNewlyCreatedAccount = true,
        isRenderingViaAccountData = false
      )
    ) {
      awaitBodyMock<HomeUiProps> {
        account.shouldBe(ActiveKeyboxLoadedDataMock.account)
      }
    }
  }

  test("No interstitial shown when rendering via account data") {
    biometricAuthService.isBiometricAuthRequiredFlow.value = false
    interstitialUiStateMachine.shouldShowInterstitial = true

    stateMachine.test(
      props = FullAccountUiProps(
        accountData = ActiveKeyboxLoadedDataMock,
        isNewlyCreatedAccount = false,
        isRenderingViaAccountData = true
      )
    ) {
      awaitBodyMock<HomeUiProps> {
        account.shouldBe(ActiveKeyboxLoadedDataMock.account)
      }
    }
  }

  test("Auth key rotation screen shown for rotating auth keys") {
    stateMachine.test(
      props = FullAccountUiProps(
        accountData = AccountData.HasActiveFullAccountData.RotatingAuthKeys(
          account = FullAccountMock,
          pendingAttempt = PendingAuthKeyRotationAttempt.ProposedAttempt
        ),
        isNewlyCreatedAccount = false,
        isRenderingViaAccountData = false
      )
    ) {
      awaitBodyMock<RotateAuthKeyUIStateMachineProps> {
        account.shouldBe(FullAccountMock)
        origin.shouldBe(RotateAuthKeyUIOrigin.PendingAttempt(PendingAuthKeyRotationAttempt.ProposedAttempt))
      }
    }
  }

  test("No longer recovering screen shown") {
    stateMachine.test(
      props = FullAccountUiProps(
        accountData = AccountData.NoLongerRecoveringFullAccountData(
          canceledRecoveryLostFactor = App
        )
      )
    ) {
      awaitBodyMock<NoLongerRecoveringUiProps> {
        canceledRecoveryLostFactor.shouldBe(App)
      }
    }
  }

  test("Someone else is recovering screen shown") {
    stateMachine.test(
      props = FullAccountUiProps(
        accountData = AccountData.SomeoneElseIsRecoveringFullAccountData(
          data = SomeoneElseIsRecoveringData.ShowingSomeoneElseIsRecoveringData(App, {}),
          fullAccountId = FullAccountIdMock
        )
      )
    ) {
      awaitBodyMock<SomeoneElseIsRecoveringUiProps> {
        fullAccountId.shouldBe(FullAccountIdMock)
      }
    }
  }
})
