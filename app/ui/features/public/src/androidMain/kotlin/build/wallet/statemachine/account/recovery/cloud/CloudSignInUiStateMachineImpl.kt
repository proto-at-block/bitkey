package build.wallet.statemachine.account.recovery.cloud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import bitkey.account.AccountConfigService
import bitkey.account.isFakeCloudStoreActive
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.cloud.store.CloudStoreAccountFake
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
  private val accountConfigService: AccountConfigService,
) : CloudSignInUiStateMachine {
  @Composable
  override fun model(props: CloudSignInUiProps): BodyModel {
    val defaultConfig by remember { accountConfigService.defaultConfig() }.collectAsState()
    val activeOrDefaultConfig by remember {
      accountConfigService.activeOrDefaultConfig()
    }.collectAsState()
    val useFakeCloudStore = isFakeCloudStoreActive(defaultConfig, activeOrDefaultConfig)

    if (useFakeCloudStore) {
      LaunchedEffect("fake-cloud-sign-in") {
        props.onSignedIn(CloudStoreAccountFake.MockCloudAccount)
      }
    } else {
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
    }

    return LoadingBodyModel(
      title = null,
      onBack = null,
      id = CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING,
      eventTrackerContext = props.eventTrackerContext
    )
  }
}
