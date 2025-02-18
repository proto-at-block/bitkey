package build.wallet.statemachine.inheritance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.bitkey.relationships.TrustedContact
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.inheritance.InheritanceClaimsRepository
import build.wallet.inheritance.InheritanceService
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.inheritance.InheritanceClaimNotificationUiStateMachineImpl.UiState.*
import build.wallet.statemachine.inheritance.claims.complete.CompleteInheritanceClaimUiStateMachine
import build.wallet.statemachine.inheritance.claims.complete.CompleteInheritanceClaimUiStateMachineProps
import build.wallet.statemachine.trustedcontact.model.BenefactorInviteAcceptedNotificationBodyModel

@BitkeyInject(ActivityScope::class)
class InheritanceClaimNotificationUiStateMachineImpl(
  private val completeInheritanceClaimUiStateMachine: CompleteInheritanceClaimUiStateMachine,
  private val declineInheritanceClaimUiStateMachine: DeclineInheritanceClaimUiStateMachine,
  private val inheritanceClaimsRepository: InheritanceClaimsRepository,
  private val inheritanceService: InheritanceService,
) : InheritanceClaimNotificationUiStateMachine {
  @Composable
  override fun model(props: InheritanceClaimNotificationUiProps): ScreenModel {
    var uiState: UiState by remember { mutableStateOf(Loading) }

    return when (val state = uiState) {
      is Loading -> {
        when (props.action) {
          InheritanceNotificationAction.CompleteClaim -> {
            LaunchedEffect("fetch claims") {
              inheritanceClaimsRepository.fetchClaims().value.beneficiaryClaims.firstOrNull {
                it.claimId.value == props.claimId
              }?.let {
                uiState = CompleteClaim(it.relationshipId)
              }
            }
          }

          InheritanceNotificationAction.DenyClaim -> {
            uiState = DenyClaim
          }

          InheritanceNotificationAction.BenefactorInviteAccepted -> {
            // The current model assumes we only ever have a single beneficiary
            // if that changes, we'll have to pass down relationshipIds from the server instead of
            // checking locally
            LaunchedEffect("fetch relationships") {
              inheritanceService.inheritanceRelationships.collect { relationships ->
                relationships.endorsedTrustedContacts.firstOrNull {
                  it.roles.contains(TrustedContactRole.Beneficiary)
                }?.let {
                  uiState = BenefactorInviteAccepted(it)
                }
              }
            }
          }
        }
        LoadingBodyModel(id = null).asModalScreen()
      }

      is DenyClaim -> {
        declineInheritanceClaimUiStateMachine.model(
          DeclineInheritanceClaimUiProps(
            fullAccount = props.fullAccount,
            claimId = props.claimId!!,
            onBack = props.onBack,
            onClaimDeclined = props.onBack,
            onBeneficiaryRemoved = props.onBack
          )
        )
      }

      is CompleteClaim -> {
        completeInheritanceClaimUiStateMachine.model(
          CompleteInheritanceClaimUiStateMachineProps(
            relationshipId = state.relationshipId,
            account = props.fullAccount,
            onExit = props.onBack
          )
        )
      }

      is BenefactorInviteAccepted -> {
        BenefactorInviteAcceptedNotificationBodyModel(
          beneficiary = state.beneficiary,
          onDone = props.onBack
        ).asModalScreen()
      }
    }
  }

  private sealed interface UiState {
    data object Loading : UiState

    data object DenyClaim : UiState

    data class CompleteClaim(val relationshipId: RelationshipId) : UiState

    data class BenefactorInviteAccepted(val beneficiary: TrustedContact) : UiState
  }
}
