package build.wallet.statemachine.trustedcontact.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.FAILED
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.PAKE_DATA_UNAVAILABLE
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.TAMPERED
import build.wallet.bitkey.relationships.UnendorsedTrustedContact
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.trustedcontact.remove.RemoveTrustedContactUiProps
import build.wallet.statemachine.trustedcontact.remove.RemoveTrustedContactUiStateMachine
import build.wallet.statemachine.trustedcontact.view.ViewingRecoveryContactUiStateMachineImpl.State.Removing
import build.wallet.statemachine.trustedcontact.view.ViewingRecoveryContactUiStateMachineImpl.State.Viewing

@BitkeyInject(ActivityScope::class)
class ViewingRecoveryContactUiStateMachineImpl(
  private val removeTrustedContactUiStateMachine: RemoveTrustedContactUiStateMachine,
) : ViewingRecoveryContactUiStateMachine {
  @Composable
  override fun model(props: ViewingRecoveryContactProps): ScreenModel {
    var state: State by remember { mutableStateOf(Viewing) }

    return when (state) {
      Viewing ->
        ScreenModel(
          body = props.screenBody,
          bottomSheetModel = when {
            props.recoveryContact is UnendorsedTrustedContact && props.recoveryContact.authenticationState in setOf(
              FAILED,
              PAKE_DATA_UNAVAILABLE
            ) ->
              ViewingFailedContactSheetModel(
                contact = props.recoveryContact,
                onRemove = { state = Removing },
                onClosed = props.onExit
              )
            props.recoveryContact is UnendorsedTrustedContact && props.recoveryContact.authenticationState == TAMPERED ->
              ViewingTamperedContactSheetModel(
                contact = props.recoveryContact,
                onRemove = { state = Removing },
                onClosed = props.onExit
              )
            else -> ViewingTrustedContactSheetModel(
              contact = props.recoveryContact,
              onRemove = { state = Removing },
              onClosed = props.onExit
            )
          }
        )

      Removing ->
        removeTrustedContactUiStateMachine.model(
          RemoveTrustedContactUiProps(
            trustedContact = props.recoveryContact,
            account = props.account,
            onClosed = {
              props.onExit()
            }
          )
        )
    }
  }

  private sealed interface State {
    data object Viewing : State

    data object Removing : State
  }
}
