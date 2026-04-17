package build.wallet.statemachine.account.full

import androidx.compose.runtime.*
import bitkey.recovery.RecoveryStatusService
import bitkey.ui.statemachine.interstitial.InterstitialUiProps
import bitkey.ui.statemachine.interstitial.InterstitialUiStateMachine
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.auth.FullAccountAuthKeyRotationService
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.inappsecurity.BiometricAuthService
import build.wallet.recovery.Recovery
import build.wallet.recovery.Recovery.NoLongerRecovering
import build.wallet.recovery.Recovery.SomeoneElseIsRecovering
import build.wallet.statemachine.biometric.BiometricPromptProps
import build.wallet.statemachine.biometric.BiometricPromptUiStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
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
  private val fullAccountAuthKeyRotationService: FullAccountAuthKeyRotationService,
  private val recoveryStatusService: RecoveryStatusService,
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
    val recovery by remember { recoveryStatusService.status }
      .collectAsState()

    var shouldShowSomeoneElseIsRecoveringIfPresent by remember(recovery) { mutableStateOf(true) }

    return when (val currentRecovery = recovery) {
      is Recovery.Loading -> AppLoadingScreenModel()
      is NoLongerRecovering -> noLongerRecoveringUiStateMachine.model(
        props = NoLongerRecoveringUiProps(
          canceledRecoveryLostFactor = currentRecovery.cancelingRecoveryLostFactor
        )
      )
      is SomeoneElseIsRecovering -> {
        if (shouldShowSomeoneElseIsRecoveringIfPresent) {
          someoneElseIsRecoveringUiStateMachine.model(
            props = SomeoneElseIsRecoveringUiProps(
              cancelingRecoveryLostFactor = currentRecovery.cancelingRecoveryLostFactor,
              fullAccountId = props.account.accountId,
              onClose = { shouldShowSomeoneElseIsRecoveringIfPresent = false }
            )
          )
        } else {
          HasActiveFullAccountDataScreenModel(
            account = props.account,
            isNewlyCreatedAccount = props.isNewlyCreatedAccount
          )
        }
      }
      Recovery.NoActiveRecovery, is Recovery.StillRecovering -> HasActiveFullAccountDataScreenModel(
        account = props.account,
        isNewlyCreatedAccount = props.isNewlyCreatedAccount
      )
    }
  }

  @Composable
  private fun HasActiveFullAccountDataScreenModel(
    account: FullAccount,
    isNewlyCreatedAccount: Boolean,
  ): ScreenModel {
    val pendingAuthKeyRotationAttempt by remember {
      fullAccountAuthKeyRotationService.observePendingKeyRotationAttemptUntilNull()
    }.collectAsState(initial = null)

    pendingAuthKeyRotationAttempt?.let {
      return authKeyRotationUiStateMachine.model(
        RotateAuthKeyUIStateMachineProps(
          account = account,
          origin = RotateAuthKeyUIOrigin.PendingAttempt(it)
        )
      )
    }

    val shouldPromptForAuth by remember { biometricAuthService.isBiometricAuthRequired() }
      .collectAsState()

    val homeScreenModel = homeUiStateMachine.model(
      props = HomeUiProps(
        account = account
      )
    )

    return biometricPromptUiStateMachine.model(
      props = BiometricPromptProps(
        shouldPromptForAuth = shouldPromptForAuth
      )
    ) ?: interstitialUiStateMachine.model(
      props = InterstitialUiProps(
        account = account,
        isComingFromOnboarding = isNewlyCreatedAccount
      )
    ) ?: homeScreenModel
  }

  @Composable
  private fun AppLoadingScreenModel(): ScreenModel {
    return LoadingSuccessBodyModel(
      id = GeneralEventTrackerScreenId.LOADING_APP,
      state = LoadingSuccessBodyModel.State.Loading
    ).asRootScreen()
  }
}
