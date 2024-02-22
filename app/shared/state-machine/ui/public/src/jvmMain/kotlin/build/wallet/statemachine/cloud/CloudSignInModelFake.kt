package build.wallet.statemachine.cloud

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
  val signInFailure: () -> Unit,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo =
    EventTrackerScreenInfo(
      eventTrackerScreenId = CLOUD_SIGN_IN_LOADING,
      eventTrackerScreenIdContext = null,
      eventTrackerShouldTrack = false
    ),
) : BodyModel()
