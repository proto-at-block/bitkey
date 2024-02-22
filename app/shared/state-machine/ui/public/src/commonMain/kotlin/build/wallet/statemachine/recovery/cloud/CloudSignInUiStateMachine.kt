package build.wallet.statemachine.recovery.cloud

import build.wallet.analytics.events.screen.context.CloudEventTrackerScreenIdContext
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine responsible for signing into cloud storage account of customer's choice.
 *
 * On Android, this goes through Google Sign In flow (unless [Props.forceSignOut] is `true` and
 * a logged out account is already present).
 * On iOS, this looks up currently signed iCloud account if any.
 *
 * Used both during onboarding for saving a backup and during recovery for accessing a backup.
 */
interface CloudSignInUiStateMachine : StateMachine<CloudSignInUiProps, BodyModel>

/**
 * @property forceSignOut indicates if any currently logged in account should be force logged
 * out first. This is only applicable to Google Sign In on Android. If [forceSignOut] is `false`
 * and customer has already previously signed in with a Google account before, we will use that
 * account right away, instead of asking customer to go through sign in flow.
 * @property onSignInFailure handler for when customer cancels sign in (in case of Google drive),
 * we can't find a logged in account (in case of iCloud), or something else goes wrong.
 * @property onSignedIn handler for when customer successfully logged into a cloud account; or
 * we found an existing logged in account (only possible if [forceSignOut] is `false`).
 * @property eventTrackerContext context for screen events emitted by this state machine to
 * disambiguate between onboarding and recovery events
 */
data class CloudSignInUiProps(
  val forceSignOut: Boolean,
  val onSignedIn: (CloudStoreAccount) -> Unit,
  val onSignInFailure: () -> Unit,
  val eventTrackerContext: CloudEventTrackerScreenIdContext,
)
