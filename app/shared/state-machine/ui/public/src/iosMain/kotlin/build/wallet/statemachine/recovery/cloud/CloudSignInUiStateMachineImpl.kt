package build.wallet.statemachine.recovery.cloud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.time.Delayer
import build.wallet.time.withMinimumDelay
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@Suppress("unused") // Used by iOS code.
class CloudSignInUiStateMachineImpl(
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
  private val delayer: Delayer,
) : CloudSignInUiStateMachine {
  @Composable
  override fun model(props: CloudSignInUiProps): BodyModel {
    LaunchedEffect("checking-account") {
      delayer
        .withMinimumDelay { cloudStoreAccountRepository.currentAccount(cloudServiceProvider()) }
        .onSuccess { account ->
          when (account) {
            null -> props.onSignInFailure(Error("No cloud store account found."))
            else -> props.onSignedIn(account)
          }
        }
        .onFailure(props.onSignInFailure)
    }

    return LoadingBodyModel(
      message = "Loading...",
      onBack = null,
      id = CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING,
      eventTrackerScreenIdContext = props.eventTrackerContext
    )
  }
}
