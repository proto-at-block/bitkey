package build.wallet.statemachine.cloud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreServiceProvider
import build.wallet.cloud.store.WritableCloudStoreAccountRepository
import build.wallet.logging.log
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.recovery.cloud.CloudSignInUiProps
import build.wallet.statemachine.recovery.cloud.CloudSignInUiStateMachine
import com.github.michaelbull.result.getOrThrow

class CloudSignInUiStateMachineFake(
  private val cloudStoreAccountRepository: WritableCloudStoreAccountRepository,
  private val cloudStoreServiceProvider: CloudStoreServiceProvider,
) : CloudSignInUiStateMachine {
  @Composable
  override fun model(props: CloudSignInUiProps): BodyModel {
    var uiState: State by remember {
      val initialState = if (props.forceSignOut) State.SigningOut else State.CheckIfAlreadySignedIn
      mutableStateOf(initialState)
    }
    return when (val state = uiState) {
      is State.SigningOut -> {
        LaunchedEffect("sign-out") {
          cloudStoreAccountRepository.clear()
          log { "Signed out of cloud" }
          uiState = State.SigningIn
        }
        LoadingModel(props)
      }

      is State.CheckIfAlreadySignedIn -> {
        LaunchedEffect("check-signed-in") {
          val currentAccount =
            cloudStoreAccountRepository.currentAccount(cloudStoreServiceProvider)
              .getOrThrow()
          uiState =
            if (currentAccount != null) {
              State.FinishSignInState(currentAccount)
            } else {
              State.SigningIn
            }
        }
        LoadingModel(props)
      }

      is State.SigningIn -> {
        CloudSignInModelFake(
          signInSuccess = {
            uiState = State.FinishSignInState(it)
          },
          signInFailure = props.onSignInFailure
        )
      }

      is State.FinishSignInState -> {
        LaunchedEffect("finish-sign-in") {
          cloudStoreAccountRepository.set(state.account).getOrThrow()
          log { "Signed in to cloud as ${state.account}" }
          props.onSignedIn(state.account)
        }
        LoadingModel(props)
      }
    }
  }

  @Composable
  private fun LoadingModel(props: CloudSignInUiProps): BodyModel =
    LoadingBodyModel(
      message = null,
      onBack = null,
      id = CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING,
      eventTrackerScreenIdContext = props.eventTrackerContext
    )

  private sealed interface State {
    data object SigningOut : State

    data object SigningIn : State

    data object CheckIfAlreadySignedIn : State

    data class FinishSignInState(val account: CloudStoreAccount) : State
  }
}
