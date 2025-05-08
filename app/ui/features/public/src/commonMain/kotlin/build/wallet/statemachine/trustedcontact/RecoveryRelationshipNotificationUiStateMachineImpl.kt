package build.wallet.statemachine.trustedcontact

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import bitkey.relationships.Relationships
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.bitkey.relationships.TrustedContact
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.inheritance.InheritanceService
import build.wallet.recovery.socrec.SocRecService
import build.wallet.statemachine.core.*
import build.wallet.statemachine.inheritance.InheritanceAppSegment
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.trustedcontact.RecoveryRelationshipNotificationUiStateMachineImpl.UiState.*
import build.wallet.statemachine.trustedcontact.model.BenefactorInviteAcceptedNotificationBodyModel
import build.wallet.statemachine.trustedcontact.model.ProtectedCustomerInviteAcceptedNotificationBodyModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

@BitkeyInject(ActivityScope::class)
class RecoveryRelationshipNotificationUiStateMachineImpl(
  private val socRecService: SocRecService,
  private val inheritanceService: InheritanceService,
) : RecoveryRelationshipNotificationUiStateMachine {
  @Composable
  override fun model(props: RecoveryRelationshipNotificationUiProps): ScreenModel {
    val relationships = when (props.action) {
      RecoveryRelationshipNotificationAction.BenefactorInviteAccepted -> inheritanceService.inheritanceRelationships
      RecoveryRelationshipNotificationAction.ProtectedCustomerInviteAccepted -> socRecService.socRecRelationships
    }
    val uiState by relationships
      .filterNotNull()
      .toStateMatching(props.recoveryRelationshipId)
      .collectAsState(Loading)

    return when (val state = uiState) {
      is Loading -> LoadingBodyModel(
        id = when (props.action) {
          RecoveryRelationshipNotificationAction.BenefactorInviteAccepted -> SocialRecoveryEventTrackerScreenId.BENEFACTOR_RECOVERY_RELATIONSHIP_LOADING
          RecoveryRelationshipNotificationAction.ProtectedCustomerInviteAccepted -> SocialRecoveryEventTrackerScreenId.PROTECTED_CUSTOMER_RECOVERY_RELATIONSHIP_LOADING
        }
      ).asModalScreen()
      is Endorsing -> LoadingBodyModel(
        id = when (props.action) {
          RecoveryRelationshipNotificationAction.BenefactorInviteAccepted -> SocialRecoveryEventTrackerScreenId.BENEFACTOR_RECOVERY_RELATIONSHIP_AWAITING_ENDORSEMENT
          RecoveryRelationshipNotificationAction.ProtectedCustomerInviteAccepted -> SocialRecoveryEventTrackerScreenId.PROTECTED_CUSTOMER_RECOVERY_RELATIONSHIP_AWAITING_ENDORSEMENT
        },
        message = "Completing invitation..."
      ).asModalScreen()
      is Endorsed -> when (props.action) {
        RecoveryRelationshipNotificationAction.BenefactorInviteAccepted -> BenefactorInviteAcceptedNotificationBodyModel(
          beneficiary = state.contact,
          onDone = props.onBack
        ).asModalScreen()
        RecoveryRelationshipNotificationAction.ProtectedCustomerInviteAccepted -> ProtectedCustomerInviteAcceptedNotificationBodyModel(
          trustedContact = state.contact,
          onDone = props.onBack
        ).asModalScreen()
      }
      is NotFound -> ErrorFormBodyModel(
        title = when (props.action) {
          RecoveryRelationshipNotificationAction.BenefactorInviteAccepted -> "Beneficiary is no longer active."
          RecoveryRelationshipNotificationAction.ProtectedCustomerInviteAccepted -> "Recovery relationship is no longer active."
        },
        toolbar =
          ToolbarModel(
            leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(props.onBack)
          ),
        primaryButton =
          ButtonDataModel(
            text = "Close",
            onClick = props.onBack
          ),
        errorData = ErrorData(
          when (props.action) {
            RecoveryRelationshipNotificationAction.BenefactorInviteAccepted -> InheritanceAppSegment.Benefactor.Invite
            RecoveryRelationshipNotificationAction.ProtectedCustomerInviteAccepted -> RecoverySegment.SocRec.TrustedContact.Setup
          },
          "User opens app from notification to complete contact invitation",
          Error("Recovery relationship for notification is not active")
        ),
        eventTrackerScreenId = when (props.action) {
          RecoveryRelationshipNotificationAction.BenefactorInviteAccepted -> SocialRecoveryEventTrackerScreenId.BENEFACTOR_RECOVERY_RELATIONSHIP_NOT_ACTIVE
          RecoveryRelationshipNotificationAction.ProtectedCustomerInviteAccepted -> SocialRecoveryEventTrackerScreenId.PROTECTED_CUSTOMER_RECOVERY_RELATIONSHIP_NOT_ACTIVE
        }
      ).asModalScreen()
    }
  }

  private fun Flow<Relationships>.toStateMatching(matchId: RelationshipId): Flow<UiState> {
    return map {
      val endorsed = it.endorsedTrustedContacts.find { it.id == matchId }
      val unendorsed = it.unendorsedTrustedContacts.find { it.id == matchId }

      when {
        unendorsed != null -> Endorsing(unendorsed)
        endorsed != null -> Endorsed(endorsed)
        else -> NotFound
      }
    }
  }

  private sealed interface UiState {
    data object Loading : UiState

    data class Endorsing(val contact: TrustedContact) : UiState

    data class Endorsed(val contact: TrustedContact) : UiState

    data object NotFound : UiState
  }
}
