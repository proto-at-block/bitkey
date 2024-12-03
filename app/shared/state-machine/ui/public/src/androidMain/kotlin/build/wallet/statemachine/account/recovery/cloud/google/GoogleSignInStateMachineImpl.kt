package build.wallet.statemachine.account.recovery.cloud.google

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.GoogleDrive
import build.wallet.google.signin.GoogleSignInError
import build.wallet.google.signin.GoogleSignInError.GoogleSignInAccountMissing
import build.wallet.google.signin.GoogleSignInError.UnhandledError
import build.wallet.google.signin.GoogleSignInLauncher
import build.wallet.google.signin.GoogleSignOutAction
import build.wallet.google.signin.GoogleSignOutError
import build.wallet.logging.*
import build.wallet.logging.logFailure
import build.wallet.statemachine.account.recovery.cloud.google.GoogleSignInStateMachineImpl.State.FailedToSignInState
import build.wallet.statemachine.account.recovery.cloud.google.GoogleSignInStateMachineImpl.State.FailedToSignOutState
import build.wallet.statemachine.account.recovery.cloud.google.GoogleSignInStateMachineImpl.State.FinishedSignInState
import build.wallet.statemachine.account.recovery.cloud.google.GoogleSignInStateMachineImpl.State.SignedInState
import build.wallet.statemachine.account.recovery.cloud.google.GoogleSignInStateMachineImpl.State.SigningInStateState
import build.wallet.statemachine.account.recovery.cloud.google.GoogleSignInStateMachineImpl.State.SigningOutState
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.gms.auth.api.signin.GoogleSignInAccount

class GoogleSignInStateMachineImpl(
  private val googleSignInLauncher: GoogleSignInLauncher,
  private val googleSignOutAction: GoogleSignOutAction,
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
) : GoogleSignInStateMachine {
  @Composable
  override fun model(props: GoogleSignInProps): GoogleSignInModel {
    var state: State by remember {
      val initialState = if (props.forceSignOut) SigningOutState else SigningInStateState
      mutableStateOf(initialState)
    }

    when (state) {
      is SigningOutState -> {
        LaunchedEffect("sign-out") {
          googleSignOutAction.signOut()
            .onSuccess {
              logDebug { "Successfully logged out from Google" }
              state = SigningInStateState
            }
            .logFailure { "Error logging out from Google" }
            .onFailure { error ->
              state = FailedToSignOutState(error)
            }
        }
      }

      else -> Unit
    }

    when (state) {
      is SigningInStateState -> {
        googleSignInLauncher.launchedGoogleSignIn(
          onSignInSuccess = {
            state = FinishedSignInState(it)
          },
          onSignInFailure = {
            logWarn { "Google sign-in failure: $it" }
            // TODO(W-678): show appropriate UI/message based on the cause of failure.
            state = FailedToSignInState(it)
          }
        )
      }

      is FinishedSignInState -> {
        LaunchedEffect("check-cloud-account") {
          cloudStoreAccountRepository.currentAccount(GoogleDrive)
            .onSuccess { account ->
              state =
                when (account) {
                  null ->
                    FailedToSignInState(
                      failure = GoogleSignInAccountMissing(cause = null)
                    )

                  else -> SignedInState(account)
                }
            }
            .onFailure { error ->
              state =
                FailedToSignInState(
                  failure =
                    UnhandledError(
                      message = "Failed to get GoogleSignInAccount. $error",
                      cause = null
                    )
                )
            }
        }
      }

      else -> {}
    }

    return when (val s = state) {
      is FinishedSignInState,
      // Explicit sign out from an existing account is part of overall sign in process.
      is SigningOutState,
      is SigningInStateState,
      -> GoogleSignInModel.SigningIn

      is SignedInState -> GoogleSignInModel.SuccessfullySignedIn(s.cloudAccount)
      is FailedToSignOutState -> GoogleSignInModel.SignInFailure(s.failure)
      is FailedToSignInState -> GoogleSignInModel.SignInFailure(s.failure)
    }
  }

  private sealed class State {
    /**
     * Currently in the process of signing out from currently logged in account, if any.
     */
    data object SigningOutState : State()

    /**
     * Waiting for customer to sign in.
     */
    data object SigningInStateState : State()

    /**
     * Just finished sign in successfully.
     */
    data class FinishedSignInState(val account: GoogleSignInAccount) : State()

    /**
     * We already have a signed in account, or customer just successfully signed in.
     */
    data class SignedInState(val cloudAccount: CloudStoreAccount) : State()

    /**
     * Sign in failed or canceled by customer.
     */
    data class FailedToSignInState(val failure: GoogleSignInError) : State()

    /**
     * Sign out failed or canceled by customer. This is only possible when [GoogleSignInProps.forceSignOut] is
     * enabled.
     */
    data class FailedToSignOutState(val failure: GoogleSignOutError) : State()
  }
}
