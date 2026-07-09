package build.wallet.statemachine.recovery.socrec.view

import androidx.compose.runtime.*
import bitkey.auth.AuthTokenScope
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_PROTECTED_CUSTOMER_SHEET_REMOVAL_FAILURE
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.ktor.result.HttpError
import build.wallet.relationships.RelationshipsService
import build.wallet.statemachine.auth.ActionProofType
import build.wallet.statemachine.auth.HardwareAuthUiProps
import build.wallet.statemachine.auth.HardwareAuthUiStateMachine
import build.wallet.statemachine.core.*
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.ui.model.alert.ButtonAlertModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@BitkeyInject(ActivityScope::class)
class ViewingProtectedCustomerUiStateMachineImpl(
  private val relationshipsService: RelationshipsService,
  private val hardwareAuthUiStateMachine: HardwareAuthUiStateMachine,
) : ViewingProtectedCustomerUiStateMachine {
  @Composable
  override fun model(props: ViewingProtectedCustomerProps): ScreenModel {
    var uiState: State by remember { mutableStateOf(State.ViewingProtectedCustomer) }
    var alertModel: ButtonAlertModel? by remember { mutableStateOf(null) }

    val isFullAccount = props.account is FullAccount

    val bottomSheetModel =
      when (val state = uiState) {
        is State.ViewingProtectedCustomer -> {
          ProtectedCustomerBottomSheetModel(
            protectedCustomer = props.protectedCustomer,
            isRemoveSelfAsTrustedContactButtonLoading = false,
            onHelpWithRecovery = props.onHelpWithRecovery,
            onRemoveSelfAsTrustedContact = {
              if (isFullAccount) {
                // Full Account: go to full-screen removal confirmation, then HW tap
                uiState = State.ConfirmingRemoval
              } else {
                // Lite/Software: show alert, then remove directly (no HW tap)
                alertModel =
                  RemoveMyselfAsTrustedContactAlertModel(
                    alias = props.protectedCustomer.alias.alias,
                    onDismiss = { alertModel = null },
                    onRemove = {
                      alertModel = null
                      uiState = State.RemovingWithProof(proof = null)
                    }
                  )
              }
            },
            onClosed = props.onExit
          )
        }

        is State.ConfirmingRemoval -> {
          // Full Account: full-screen modal confirmation (matches RemoveTrustedContactBodyModel pattern)
          return RemoveMyselfAsTrustedContactBodyModel(
            protectedCustomerAlias = props.protectedCustomer.alias.alias,
            onRemove = {
              uiState = State.ScanningHardware
            },
            onClosed = {
              uiState = State.ViewingProtectedCustomer
            }
          ).asModalScreen()
        }

        is State.ScanningHardware -> {
          val fullAccount = props.account as FullAccount
          return hardwareAuthUiStateMachine.model(
            HardwareAuthUiProps(
              account = fullAccount,
              actionProofType = ActionProofType.RemoveRecoveryCustomer(
                entityId = props.protectedCustomer.id.value,
                name = props.protectedCustomer.alias.alias
              ),
              segment = RecoverySegment.SocRec.TrustedContact,
              actionDescription = "Removing self as Recovery Contact",
              screenPresentationStyle = ScreenPresentationStyle.Modal,
              onSuccess = { proof ->
                uiState = State.RemovingWithProof(proof)
              },
              onBack = {
                uiState = State.ConfirmingRemoval
              }
            )
          )
        }

        is State.RemovingWithProof -> {
          LaunchedEffect("remove-tc-with-proof") {
            relationshipsService.removeRelationship(
              account = props.account,
              proof = state.proof,
              authTokenScope = if (state.proof != null) AuthTokenScope.Global else AuthTokenScope.Recovery,
              relationshipId = props.protectedCustomer.id.value
            )
              .onSuccess {
                props.onExit()
              }
              .onFailure { error ->
                uiState = State.ViewingFailedToRemoveError(
                  proof = state.proof,
                  error = error
                )
              }
          }
          if (state.proof != null) {
            // Full Account path: show loading modal (matching RemoveTrustedContactUiStateMachineImpl)
            return LoadingBodyModel(
              id = SocialRecoveryEventTrackerScreenId.TC_MANAGEMENT_REMOVAL_LOADING
            ).asModalScreen()
          }
          // Lite/Software path: show bottom sheet with loading indicator
          ProtectedCustomerBottomSheetModel(
            protectedCustomer = props.protectedCustomer,
            isRemoveSelfAsTrustedContactButtonLoading = true,
            onHelpWithRecovery = props.onHelpWithRecovery,
            onRemoveSelfAsTrustedContact = {},
            onClosed = props.onExit
          )
        }

        is State.ViewingFailedToRemoveError -> {
          if (state.proof != null) {
            // Full Account path: full-screen error with retry (matching RemoveTrustedContactUiStateMachineImpl)
            return NetworkErrorFormBodyModel(
              eventTrackerScreenId = SocialRecoveryEventTrackerScreenId.TC_MANAGEMENT_REMOVAL_FAILED,
              title = "Unable to remove yourself as a Recovery Contact",
              isConnectivityError = state.error is HttpError.NetworkError,
              errorData = ErrorData(
                segment = RecoverySegment.SocRec.TrustedContact,
                actionDescription = "Removing self as Recovery Contact",
                cause = state.error
              ),
              onRetry = {
                uiState = State.RemovingWithProof(proof = state.proof)
              },
              onBack = {
                uiState = State.ConfirmingRemoval
              }
            ).asModalScreen()
          }
          // Lite/Software path: bottom sheet error
          ErrorFormBottomSheetModel(
            title = "We couldn't remove you as a Recovery Contact",
            subline = "There was a problem removing yourself as a Recovery Contact. Please try again.",
            primaryButton = ButtonDataModel("Back", onClick = { props.onExit() }),
            errorData = ErrorData(
              segment = RecoverySegment.SocRec.TrustedContact,
              actionDescription = "Removing self as Recovery Contact",
              cause = state.error
            ),
            onClosed = props.onExit,
            eventTrackerScreenId = TC_PROTECTED_CUSTOMER_SHEET_REMOVAL_FAILURE
          )
        }
      }

    return props.screenModel.copy(
      bottomSheetModel = bottomSheetModel,
      alertModel = alertModel
    )
  }

  private sealed interface State {
    /** Initial sheet state, user can view and request removal */
    data object ViewingProtectedCustomer : State

    /** Full-screen removal confirmation before HW tap (Full Account TCs) */
    data object ConfirmingRemoval : State

    /** Scanning hardware for action proof (Full Account TCs only) */
    data object ScanningHardware : State

    /** Removing with an optional proof (null for Lite/Software accounts) */
    data class RemovingWithProof(val proof: PrivilegedActionProof?) : State

    /** Error state */
    data class ViewingFailedToRemoveError(
      val proof: PrivilegedActionProof?,
      val error: Error,
    ) : State
  }
}
