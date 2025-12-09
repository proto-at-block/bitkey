package build.wallet.statemachine.account.full

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import bitkey.ui.statemachine.interstitial.InterstitialUiProps
import bitkey.ui.statemachine.interstitial.InterstitialUiStateMachine
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.inappsecurity.BiometricAuthService
import build.wallet.statemachine.biometric.BiometricPromptProps
import build.wallet.statemachine.biometric.BiometricPromptUiStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.data.keybox.AccountData.*
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData
import build.wallet.statemachine.home.full.HomeUiProps
import build.wallet.statemachine.home.full.HomeUiStateMachine
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIOrigin
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIStateMachine
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIStateMachineProps
import build.wallet.statemachine.recovery.conflict.NoLongerRecoveringUiProps
import build.wallet.statemachine.recovery.conflict.NoLongerRecoveringUiStateMachine
import build.wallet.statemachine.recovery.conflict.SomeoneElseIsRecoveringUiProps
import build.wallet.statemachine.recovery.conflict.SomeoneElseIsRecoveringUiStateMachine

@BitkeyInject(ActivityScope::class)
class FullAccountUiStateMachineImpl(
  private val homeUiStateMachine: HomeUiStateMachine,
  private val noLongerRecoveringUiStateMachine: NoLongerRecoveringUiStateMachine,
  private val someoneElseIsRecoveringUiStateMachine: SomeoneElseIsRecoveringUiStateMachine,
  private val authKeyRotationUiStateMachine: RotateAuthKeyUIStateMachine,
  private val biometricAuthService: BiometricAuthService,
  private val biometricPromptUiStateMachine: BiometricPromptUiStateMachine,
  private val interstitialUiStateMachine: InterstitialUiStateMachine,
) : FullAccountUiStateMachine {
  @Composable
  override fun model(props: FullAccountUiProps): ScreenModel {
    return when (val accountData = props.accountData) {
      is CheckingActiveAccountData -> AppLoadingScreenModel()

      is HasActiveFullAccountData -> HasActiveFullAccountDataScreenModel(
        accountData = accountData,
        isNewlyCreatedAccount = props.isNewlyCreatedAccount,
        isRenderingViaAccountData = props.isRenderingViaAccountData
      )

      is NoLongerRecoveringFullAccountData -> noLongerRecoveringUiStateMachine.model(
        props = NoLongerRecoveringUiProps(
          canceledRecoveryLostFactor = accountData.canceledRecoveryLostFactor
        )
      )

      is SomeoneElseIsRecoveringFullAccountData -> someoneElseIsRecoveringUiStateMachine.model(
        props = SomeoneElseIsRecoveringUiProps(
          data = accountData.data,
          fullAccountId = accountData.fullAccountId
        )
      )

      else -> error("Unexpected account data type: ${accountData::class.simpleName}")
    }
  }

  @Composable
  private fun HasActiveFullAccountDataScreenModel(
    accountData: HasActiveFullAccountData,
    isNewlyCreatedAccount: Boolean,
    isRenderingViaAccountData: Boolean,
  ): ScreenModel {
    val shouldPromptForAuth by remember { biometricAuthService.isBiometricAuthRequired() }
      .collectAsState()

    return when (accountData) {
      is ActiveFullAccountLoadedData -> {
        val homeScreenModel = homeUiStateMachine.model(
          props = HomeUiProps(
            account = accountData.account
          )
        )

        biometricPromptUiStateMachine.model(
          props = BiometricPromptProps(
            shouldPromptForAuth = shouldPromptForAuth
          )
        ) ?: interstitialUiStateMachine.model(
          props = InterstitialUiProps(
            account = accountData.account,
            // if we are rendering via account data, we are coming from onboarding since the
            // only time we render ActiveFullAccountLoadedData via "RenderingViaAccountData" is
            // when we are coming from onboarding.
            isComingFromOnboarding = isNewlyCreatedAccount || isRenderingViaAccountData
          )
        ) ?: homeScreenModel
      }

      is HasActiveFullAccountData.RotatingAuthKeys ->
        authKeyRotationUiStateMachine.model(
          RotateAuthKeyUIStateMachineProps(
            account = accountData.account,
            origin = RotateAuthKeyUIOrigin.PendingAttempt(accountData.pendingAttempt)
          )
        )
    }
  }

  @Composable
  private fun AppLoadingScreenModel(): ScreenModel {
    return LoadingSuccessBodyModel(
      id = GeneralEventTrackerScreenId.LOADING_APP,
      state = LoadingSuccessBodyModel.State.Loading
    ).asRootScreen()
  }
}
