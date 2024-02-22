package build.wallet.statemachine.dev.cloud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.cloud.store.GoogleAccountRepository
import build.wallet.compose.collections.immutableListOf
import build.wallet.google.signin.GoogleSignInLauncher
import build.wallet.google.signin.GoogleSignOutAction
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.dev.DebugMenuBodyModel
import build.wallet.ui.model.Click.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle.CARD_GROUP
import build.wallet.ui.model.list.ListItemAccessory.ButtonAccessory
import build.wallet.ui.model.list.ListItemModel
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.Scope

/**
 * Android implementation of [CloudDevOptionsStateMachine].
 *
 * Allows to sign in and sign out from Google Account, view account information and granted scopes.
 */
class CloudDevOptionsStateMachineImpl(
  private val googleAccountRepository: GoogleAccountRepository,
  private val googleSignInLauncher: GoogleSignInLauncher,
  private val googleSignOutAction: GoogleSignOutAction,
) : CloudDevOptionsStateMachine {
  @Composable
  override fun model(props: CloudDevOptionsProps): BodyModel {
    var account: Result<GoogleSignInAccount?, Error> by remember { mutableStateOf(Ok(null)) }

    // List of granted scopes for the current Google Account.
    val googleGrantedScopes = remember(account) {
      account.get()
        ?.grantedScopes
        ?.mapNotNull { it.uriId() }
        ?.sorted()
        ?.joinToString("\n") { it }
    }

    var refreshingAccount by remember { mutableStateOf(true) }
    if (refreshingAccount) {
      LaunchedEffect("google-account") {
        account = googleAccountRepository.currentAccount().map { it?.credentials }
        refreshingAccount = false
      }
    }

    var signingIn by remember { mutableStateOf(false) }
    if (signingIn) {
      googleSignInLauncher.launchedGoogleSignIn(
        onSignInSuccess = {
          refreshingAccount = true
          signingIn = false
        },
        onSignInFailure = {
          refreshingAccount = true
          signingIn = false
        }
      )
    }

    var signingOut by remember { mutableStateOf(false) }
    if (signingOut) {
      LaunchedEffect("google-sign-out") {
        googleSignOutAction.signOut()
        refreshingAccount = true
        signingOut = false
      }
    }

    return DebugMenuBodyModel(
      title = "Cloud Storage",
      onBack = props.onExit,
      groups = immutableListOf(
        ListGroupModel(
          header = "Google Account & Google Drive",
          items = immutableListOf(
            ListItemModel(
              title = "Email",
              sideText = account.get()?.email.toString()
            ),
            ListItemModel(
              title = "Granted scopes",
              sideText = googleGrantedScopes
            )
          ),
          style = CARD_GROUP
        ),
        ListGroupModel(
          header = "Google Sign In actions",
          items = immutableListOf(
            ListItemModel(
              title = "Sign In",
              secondaryText = "This will sign in to the Google Account and Google Drive.",
              trailingAccessory = ButtonAccessory(
                ButtonModel(
                  text = "Sign In",
                  treatment = ButtonModel.Treatment.SecondaryDestructive,
                  size = ButtonModel.Size.Compact,
                  isLoading = signingIn,
                  onClick = StandardClick { signingIn = true }
                )
              )
            ),
            ListItemModel(
              title = "Sign Out from Google Account",
              secondaryText = "This will sign out of the Google Account and Google Drive.",
              trailingAccessory = ButtonAccessory(
                ButtonModel(
                  text = "Sign Out",
                  treatment = ButtonModel.Treatment.SecondaryDestructive,
                  size = ButtonModel.Size.Compact,
                  isLoading = signingOut,
                  onClick = StandardClick { signingOut = true }
                )
              )
            )
          ),
          style = CARD_GROUP
        )
      )
    )
  }
}

private fun Scope.uriId(): String = scopeUri.substringAfterLast("/")
