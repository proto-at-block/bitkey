package build.wallet.statemachine.account.recovery.cloud.google

import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.statemachine.core.StateMachine

/**
 * A state machine that shows Google's Sign In UI. Authenticated account's permissions are scoped to
 * access to app's data on Google Drive.
 *
 */
interface GoogleSignInStateMachine : StateMachine<GoogleSignInProps, GoogleSignInModel>

/**
 * @property forceSignOut - when `true` will sign out from currently signed in account, if any.
 */
data class GoogleSignInProps(
  val forceSignOut: Boolean,
)

/**
 * Note that [GoogleSignInModel] should not be directly rendered in UI. Google Sign In UI is outside
 * of the app's reach, because of that [GoogleSignInModel] should be treated as a result-type model,
 * not a UI screen/component model.
 */
sealed class GoogleSignInModel {
  /**
   * Google Sign In UI is shown - sign in in progress.
   */
  data object SigningIn : GoogleSignInModel()

  /**
   * Google Sign In UI is no longer shown - customer successfully signed in.
   */
  data class SuccessfullySignedIn(val account: CloudStoreAccount) : GoogleSignInModel()

  /**
   * Google Sign In UI is no longer shown - failed or canceled by customer.
   */
  data class SignInFailure(val message: String) : GoogleSignInModel()
}
