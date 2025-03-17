package build.wallet.statemachine.cloud

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.statemachine.core.BodyModel

/**
 * Model for fake cloud sign in screen.
 *
 * @param signInSuccess callback to be called to simulate successful sign in. The provided
 * [CloudStoreAccount] will be used to simulate a successful sign in.
 * @param signInFailure callback to be called to simulate sign in failure.
 */
data class CloudSignInModelFake(
  val signInSuccess: (account: CloudStoreAccount) -> Unit,
  val signInFailure: (Error) -> Unit,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo =
    EventTrackerScreenInfo(
      eventTrackerScreenId = CLOUD_SIGN_IN_LOADING,
      eventTrackerContext = null,
      eventTrackerShouldTrack = false
    ),
) : BodyModel() {
  @Composable
  override fun render(modifier: Modifier) {
    error("Not implemented")
  }
}
