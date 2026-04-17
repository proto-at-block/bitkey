package build.wallet.statemachine.inheritance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import bitkey.auth.AuthTokenScope
import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.bitkey.relationships.TrustedContact
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.inheritance.InheritanceService
import build.wallet.relationships.RelationshipsService
import build.wallet.statemachine.auth.ActionProofType
import build.wallet.statemachine.auth.HardwareAuthUiProps
import build.wallet.statemachine.auth.HardwareAuthUiStateMachine
import build.wallet.statemachine.core.*
import com.github.michaelbull.result.*

@BitkeyInject(ActivityScope::class)
class RemovingRelationshipUiStateMachineImpl(
  private val hardwareAuthUiStateMachine: HardwareAuthUiStateMachine,
  private val inheritanceService: InheritanceService,
  private val relationshipsService: RelationshipsService,
) : RemovingRelationshipUiStateMachine {
  @Composable
  override fun model(props: RemovingRelationshipUiProps): ScreenModel {
    var state: State by remember {
      mutableStateOf(State.ConfirmingRelationshipRemoval(props.recoveryEntity.id))
    }

    val subject = when (props.recoveryEntity) {
      is Invitation -> "Invite"
      is TrustedContact -> "Beneficiary"
      is ProtectedCustomer -> "Benefactor"
    }
    val entityAlias = when (props.recoveryEntity) {
      is Invitation -> "Invite"
      is TrustedContact,
      is ProtectedCustomer,
      -> props.recoveryEntity.recoveryAlias
    }
    val action = when (props.recoveryEntity) {
      is Invitation -> "Cancel"
      else -> "Remove"
    }
    val headline = "$action $entityAlias?"
    val subline = when (props.recoveryEntity) {
      is Invitation -> ""
      else -> "This will cancel any active claims. "
    } + "This action cannot be undone."
    val primaryButtonText = "$action $subject"

    return when (val current = state) {
      is State.ConfirmingRelationshipRemoval -> {
        ScreenModel(
          body = props.body,
          bottomSheetModel = SheetModel(
            body = DestructiveInheritanceActionBodyModel(
              headline = headline,
              subline = subline,
              primaryButtonText = primaryButtonText,
              onClose = props.onExit,
              onPrimaryClick = { state = State.ScanningToRemoveClaim(props.recoveryEntity.id) }
            ),
            onClosed = props.onExit
          )
        )
      }

      is State.ScanningToRemoveClaim -> {
        hardwareAuthUiStateMachine.model(
          HardwareAuthUiProps(
            account = props.account,
            actionProofType = when (val entity = props.recoveryEntity) {
              // Customer removing a beneficiary or canceling an invitation: bind TC's alias.
              is TrustedContact -> ActionProofType.RemoveBeneficiary(
                entityId = current.relationshipId.value,
                name = entity.trustedContactAlias.alias
              )
              is Invitation -> ActionProofType.RemoveBeneficiary(
                entityId = current.relationshipId.value,
                name = entity.trustedContactAlias.alias
              )
              // TC (beneficiary) removing themselves from a benefactor's wallet.
              is ProtectedCustomer -> ActionProofType.RemoveBenefactor(
                entityId = current.relationshipId.value,
                name = entity.alias.alias
              )
            },
            segment = InheritanceAppSegment.BeneficiaryClaim.Cancel,
            actionDescription = "Removing inheritance relationship",
            screenPresentationStyle = ScreenPresentationStyle.Modal,
            onSuccess = { proof ->
              state =
                State.RemovingRelationship(
                  proof = proof,
                  relationshipId = current.relationshipId
                )
            },
            onBack = {
              state =
                State.ConfirmingRelationshipRemoval(
                  relationshipId = current.relationshipId
                )
            }
          )
        )
      }

      is State.RemovingRelationship -> {
        LaunchedEffect("cancel-claim") {
          inheritanceService.cancelClaims(
            current.relationshipId
          )
            .andThenRecoverIf(
              { error -> error is InheritanceService.ClaimNotFoundError },
              { _ -> Ok(Unit) }
            )
            .onFailure { error ->
              state = State.RemovingRelationshipFailed(error)
            }
            .onSuccess {
              relationshipsService.removeRelationship(
                account = props.account,
                proof = current.proof,
                authTokenScope = AuthTokenScope.Global,
                relationshipId = current.relationshipId.value
              ).onSuccess {
                props.onSuccess()
              }.onFailure { error ->
                state = State.RemovingRelationshipFailed(error)
              }
            }
        }

        ScreenModel(
          body = props.body,
          bottomSheetModel = SheetModel(
            body = DestructiveInheritanceActionBodyModel(
              headline = headline,
              subline = subline,
              primaryButtonText = primaryButtonText,
              onClose = props.onExit,
              onPrimaryClick = { },
              isLoading = true
            ),
            onClosed = props.onExit
          )
        )
      }

      is State.RemovingRelationshipFailed ->
        ScreenModel(
          body = ErrorFormBodyModel(
            title = "Failed To Remove Relationship",
            subline = "Please check your internet connection and try again.",
            primaryButton = ButtonDataModel(
              text = "Okay",
              onClick = props.onExit
            ),
            errorData = ErrorData(
              segment = InheritanceAppSegment.BeneficiaryClaim.Cancel,
              cause = current.error,
              actionDescription = "Canceling inheritance claim"
            ),
            eventTrackerScreenId = InheritanceEventTrackerScreenId.ManageInheritance
          )
        )
    }
  }

  private sealed interface State {
    data class RemovingRelationshipFailed(
      val error: Throwable,
    ) : State

    data class ScanningToRemoveClaim(
      val relationshipId: RelationshipId,
    ) : State

    data class ConfirmingRelationshipRemoval(
      val relationshipId: RelationshipId,
    ) : State

    data class RemovingRelationship(
      val relationshipId: RelationshipId,
      val proof: PrivilegedActionProof,
    ) : State
  }
}
