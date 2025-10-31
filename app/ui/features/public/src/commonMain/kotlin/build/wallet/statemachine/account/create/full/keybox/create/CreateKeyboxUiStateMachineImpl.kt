package build.wallet.statemachine.account.create.full.keybox.create

import androidx.compose.runtime.*
import bitkey.f8e.error.F8eError
import bitkey.onboarding.FullAccountCreationError
import build.wallet.analytics.events.screen.context.PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.*
import build.wallet.bitkey.keybox.KeyCrossDraft.WithAppKeys
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrolled
import build.wallet.onboarding.AppKeyAlreadyInUseError
import build.wallet.onboarding.ErrorStoringSealedCsekError
import build.wallet.onboarding.HardwareKeyAlreadyInUseError
import build.wallet.onboarding.OnboardFullAccountService
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareProps
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachine
import build.wallet.statemachine.account.create.full.hardware.PairingContext
import build.wallet.statemachine.account.create.full.keybox.create.CreateKeyboxUiStateMachineImpl.State.*
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.core.ScreenPresentationStyle.RootFullScreen
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@BitkeyInject(ActivityScope::class)
class CreateKeyboxUiStateMachineImpl(
  private val pairNewHardwareUiStateMachine: PairNewHardwareUiStateMachine,
  private val onboardFullAccountService: OnboardFullAccountService,
) : CreateKeyboxUiStateMachine {
  @Composable
  override fun model(props: CreateKeyboxUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(CreatingAppKeysState) }
    return when (val currentState = state) {
      is CreatingAppKeysState -> {
        LaunchedEffect("generate-app-keys") {
          onboardFullAccountService.createAppKeys()
            .onSuccess {
              state = HasAppKeysState(appKeys = it)
            }
            .onFailure {
              state = CreateAppKeysErrorState
            }
        }
        pairNewHardwareUiStateMachine.model(
          props = PairNewHardwareProps(
            request = PairNewHardwareProps.Request.Preparing,
            screenPresentationStyle = RootFullScreen,
            onExit = props.onExit,
            eventTrackerContext = ACCOUNT_CREATION,
            pairingContext = PairingContext.Onboarding
          )
        )
      }

      is HasAppKeysState ->
        pairNewHardwareUiStateMachine.model(
          props = PairNewHardwareProps(
            request = PairNewHardwareProps.Request.Ready(
              appGlobalAuthPublicKey = currentState.appKeys.appKeyBundle.authKey,
              onSuccess = { hwActivation ->
                state = PairingWithServerState(
                  hwActivation = hwActivation,
                  appKeys = currentState.appKeys
                )
              }
            ),
            screenPresentationStyle = Root,
            onExit = props.onExit,
            eventTrackerContext = ACCOUNT_CREATION,
            pairingContext = PairingContext.Onboarding
          )
        )

      is PairingWithServerState -> {
        LaunchedEffect("create-full-account") {
          onboardFullAccountService
            .createAccount(
              context = props.context,
              appKeys = currentState.appKeys,
              hwActivation = currentState.hwActivation
            )
            .onSuccess { fullAccount ->
              props.onAccountCreated(fullAccount)
            }
            .onFailure { accountCreationError ->
              // Account creation failed
              state = when (accountCreationError) {
                is ErrorStoringSealedCsekError -> ErrorStoringSealedCsek(appKeys = currentState.appKeys)

                is HardwareKeyAlreadyInUseError -> PairWithServerErrorHardwareKeyAlreadyInUseState(
                  hwActivation = currentState.hwActivation,
                  appKeys = currentState.appKeys
                )

                is AppKeyAlreadyInUseError -> PairWithServerErrorAppKeyAlreadyInUseState(
                  hwActivation = currentState.hwActivation,
                  appKeys = currentState.appKeys
                )

                else -> PairWithServerErrorState(
                  hwActivation = currentState.hwActivation,
                  appKeys = currentState.appKeys,
                  error = accountCreationError,
                  onRetryClick = {
                    state = PairingWithServerState(
                      appKeys = currentState.appKeys,
                      hwActivation = currentState.hwActivation
                    )
                  }
                )
              }
            }
        }

        LoadingBodyModel(
          message = "Creating account...",
          id = NEW_ACCOUNT_SERVER_KEYS_LOADING
        ).asRootScreen()
      }

      is ErrorStoringSealedCsek -> ErrorFormBodyModel(
        onBack = props.onExit,
        title = "We couldn’t create your wallet",
        subline = "There was an issue with setting up your Bitkey device. Please retry to set up your device.",
        primaryButton = ButtonDataModel(
          text = "Retry",
          onClick = { state = HasAppKeysState(appKeys = currentState.appKeys) }
        ),
        eventTrackerScreenId = NEW_ACCOUNT_CREATION_HW_FAILURE
      ).asRootScreen()
      is CreateAppKeysErrorState -> ErrorFormBodyModel(
        onBack = props.onExit,
        title = "We couldn’t create your wallet",
        subline = "We are looking into this. Please try again later.",
        primaryButton = ButtonDataModel(text = "Done", onClick = props.onExit),
        eventTrackerScreenId = APP_KEYS_CREATION_FAILURE
      ).asRootScreen()
      is PairWithServerErrorAppKeyAlreadyInUseState ->
        ErrorFormBodyModel(
          onBack = props.onExit,
          title = "We couldn’t create your wallet",
          subline = "Please try again.",
          primaryButton = ButtonDataModel(
            text = "Retry",
            onClick = {
              state = PairingWithServerState(
                appKeys = currentState.appKeys,
                hwActivation = currentState.hwActivation
              )
            }
          ),
          secondaryButton = ButtonDataModel(text = "Back", onClick = props.onExit),
          eventTrackerScreenId = NEW_ACCOUNT_CREATION_FAILURE_APP_KEY_ALREADY_IN_USE
        ).asRootScreen()
      is PairWithServerErrorHardwareKeyAlreadyInUseState ->
        ErrorFormBodyModel(
          onBack = props.onExit,
          title = "We couldn’t create your wallet",
          subline = "The Bitkey device is already being used by an existing wallet. If it belongs to you, you can use the device to restore your wallet.",
          primaryButton = ButtonDataModel(text = "Got it", onClick = props.onExit),
          eventTrackerScreenId = NEW_ACCOUNT_CREATION_FAILURE_HW_KEY_ALREADY_IN_USE
        ).asRootScreen()
      is PairWithServerErrorState ->
        ErrorFormBodyModel(
          onBack = props.onExit,
          title = "We couldn’t create your wallet",
          subline = when {
            currentState.error.isConnectivityError() -> "Make sure you are connected to the internet and try again."
            else -> "We are looking into this. Please try again later."
          },
          primaryButton = ButtonDataModel(
            text = "Retry",
            onClick = currentState.onRetryClick
          ),
          secondaryButton = ButtonDataModel(text = "Back", onClick = props.onExit),
          eventTrackerScreenId = NEW_ACCOUNT_CREATION_FAILURE
        ).asRootScreen()
    }
  }

  private sealed interface State {
    data object CreatingAppKeysState : State

    data object CreateAppKeysErrorState : State

    /**
     * App Keys generated. We can now pair with hardware, UI state machine is taking care of this.
     */
    data class HasAppKeysState(
      val appKeys: WithAppKeys,
    ) : State

    /**
     * Received an error when storing the Sealed Csek into dao
     *
     * @property appKeys - The keycross draft with app keys necessary for re-attempting csek
     * storage
     */
    data class ErrorStoringSealedCsek(
      val appKeys: WithAppKeys,
    ) : State

    /**
     * Pairing with server, creating Account.
     */
    data class PairingWithServerState(
      val hwActivation: FingerprintEnrolled,
      val appKeys: WithAppKeys,
    ) : State

    /**
     * There was an error when pairing with the server due to the HW key already being paired.
     */
    data class PairWithServerErrorHardwareKeyAlreadyInUseState(
      val hwActivation: FingerprintEnrolled,
      val appKeys: WithAppKeys,
    ) : State

    /**
     * There was an error when pairing with the server due to the app key already being paired.
     */
    data class PairWithServerErrorAppKeyAlreadyInUseState(
      val hwActivation: FingerprintEnrolled,
      val appKeys: WithAppKeys,
    ) : State

    /**
     * There was a generic error when pairing with the server.
     */
    data class PairWithServerErrorState(
      val hwActivation: FingerprintEnrolled,
      val appKeys: WithAppKeys,
      val error: Throwable,
      val onRetryClick: (() -> Unit),
    ) : State
  }

  private fun Throwable.isConnectivityError(): Boolean {
    // Check for account creation connectivity errors
    return when (val error = this) {
      is FullAccountCreationError.AccountCreationF8eError -> error.f8eError is F8eError.ConnectivityError
      else -> false
    }
  }
}
