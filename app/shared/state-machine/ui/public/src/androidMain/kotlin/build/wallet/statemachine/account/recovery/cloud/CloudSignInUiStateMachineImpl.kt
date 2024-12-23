package build.wallet.statemachine.account.recovery.cloud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.account.recovery.cloud.google.GoogleSignInModel.SignInFailure
import build.wallet.statemachine.account.recovery.cloud.google.GoogleSignInModel.SigningIn
import build.wallet.statemachine.account.recovery.cloud.google.GoogleSignInModel.SuccessfullySignedIn
import build.wallet.statemachine.account.recovery.cloud.google.GoogleSignInProps
import build.wallet.statemachine.account.recovery.cloud.google.GoogleSignInStateMachine
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.recovery.cloud.CloudSignInUiProps
import build.wallet.statemachine.recovery.cloud.CloudSignInUiStateMachine

@BitkeyInject(ActivityScope::class)
class CloudSignInUiStateMachineImpl(
  private val googleSignInStateMachine: GoogleSignInStateMachine,
) : CloudSignInUiStateMachine {
  @Composable
  override fun model(props: CloudSignInUiProps): BodyModel {
    when (
      val signInResult =
        googleSignInStateMachine.model(GoogleSignInProps(props.forceSignOut))
    ) {
      is SuccessfullySignedIn -> {
        LaunchedEffect("on-signed-in") {
          props.onSignedIn(signInResult.account)
        }
      }

      is SignInFailure -> {
        LaunchedEffect("on-sign-in-failure") {
          props.onSignInFailure(signInResult.cause)
        }
      }

      SigningIn -> Unit
    }

    return LoadingBodyModel(
      message = null,
      onBack = null,
      id = CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING,
      eventTrackerContext = props.eventTrackerContext
    )
  }
}
