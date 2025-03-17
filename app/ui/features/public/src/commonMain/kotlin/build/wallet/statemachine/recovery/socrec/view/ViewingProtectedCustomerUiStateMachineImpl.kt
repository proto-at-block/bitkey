package build.wallet.statemachine.recovery.socrec.view

import androidx.compose.runtime.*
import bitkey.auth.AuthTokenScope
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_PROTECTED_CUSTOMER_SHEET_REMOVAL_FAILURE
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.relationships.RelationshipsService
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBottomSheetModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.recovery.socrec.view.ViewingProtectedCustomerUiStateMachineImpl.State.ViewingProtectedCustomer
import build.wallet.ui.model.alert.ButtonAlertModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@BitkeyInject(ActivityScope::class)
class ViewingProtectedCustomerUiStateMachineImpl(
  private val relationshipsService: RelationshipsService,
) : ViewingProtectedCustomerUiStateMachine {
  @Composable
  override fun model(props: ViewingProtectedCustomerProps): ScreenModel {
    var uiState: State by remember { mutableStateOf(ViewingProtectedCustomer()) }
    var alertModel: ButtonAlertModel? by remember { mutableStateOf(null) }

    val bottomSheetModel =
      when (val state = uiState) {
        is ViewingProtectedCustomer -> {
          if (state.isRemoving) {
            LaunchedEffect("remove-tc") {
              relationshipsService.removeRelationship(
                account = props.account,
                hardwareProofOfPossession = null,
                authTokenScope = AuthTokenScope.Recovery,
                relationshipId = props.protectedCustomer.relationshipId
              )
                .onSuccess {
                  uiState = state.copy(isRemoving = false)
                  props.onExit()
                }
                .onFailure { _ ->
                  uiState = State.ViewingFailedToRemoveError
                }
            }
          }

          ProtectedCustomerBottomSheetModel(
            protectedCustomer = props.protectedCustomer,
            isRemoveSelfAsTrustedContactButtonLoading = state.isRemoving,
            onHelpWithRecovery = props.onHelpWithRecovery,
            onRemoveSelfAsTrustedContact = {
              alertModel =
                RemoveMyselfAsTrustedContactAlertModel(
                  alias = props.protectedCustomer.alias.alias,
                  onDismiss = { alertModel = null },
                  onRemove = {
                    alertModel = null
                    uiState = state.copy(isRemoving = true)
                  }
                )
            },
            onClosed = props.onExit
          )
        }

        is State.ViewingFailedToRemoveError ->
          ErrorFormBottomSheetModel(
            title = "We couldn’t remove you as a Trusted Contact",
            subline = "There was a problem removing yourself as a trusted contact. Please try again.",
            primaryButton = ButtonDataModel("Back", onClick = { props.onExit() }),
            onClosed = props.onExit,
            eventTrackerScreenId = TC_PROTECTED_CUSTOMER_SHEET_REMOVAL_FAILURE
          )
      }

    return props.screenModel.copy(
      bottomSheetModel = bottomSheetModel,
      alertModel = alertModel
    )
  }

  private sealed interface State {
    data class ViewingProtectedCustomer(
      val isRemoving: Boolean = false,
    ) : State

    data object ViewingFailedToRemoveError : State
  }
}
