package build.wallet.statemachine.trustedcontact

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
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

@BitkeyInject(ActivityScope::class)
class RecoveryRelationshipNotificationUiStateMachineImpl(
  private val socRecService: SocRecService,
  private val inheritanceService: InheritanceService,
) : RecoveryRelationshipNotificationUiStateMachine {
  @Composable
  override fun model(props: RecoveryRelationshipNotificationUiProps): ScreenModel {
    var uiState: UiState by remember { mutableStateOf(Loading) }

    return when (val state = uiState) {
      is Loading -> {
        when (props.action) {
          RecoveryRelationshipNotificationAction.BenefactorInviteAccepted -> {
            LaunchedEffect("fetch inheritance relationships") {
              inheritanceService.inheritanceRelationships.collect { relationships ->
                val matchingContact = relationships.endorsedTrustedContacts
                  .firstOrNull { it.id.toString() == props.recoveryRelationshipId }

                uiState = if (matchingContact != null) {
                  BenefactorInviteAccepted(matchingContact)
                } else {
                  createRelationshipNotExistState(
                    SocialRecoveryEventTrackerScreenId.BENEFACTOR_RECOVERY_RELATIONSHIP_NOT_ACTIVE,
                    InheritanceAppSegment.Benefactor.Invite,
                    "When the benefactor opens up the push notification to endorse the inheritance recovery relationship, it isn't active."
                  )
                }
              }
            }
          }

          RecoveryRelationshipNotificationAction.ProtectedCustomerInviteAccepted -> {
            LaunchedEffect("fetch socrec relationships") {
              socRecService.socRecRelationships.collect { relationships ->
                val matchingContact = relationships?.let { rel ->
                  (rel.endorsedTrustedContacts + rel.unendorsedTrustedContacts)
                    .firstOrNull { it.id.toString() == props.recoveryRelationshipId }
                }

                uiState = if (matchingContact != null) {
                  ProtectedCustomerInviteAccepted(matchingContact)
                } else {
                  createRelationshipNotExistState(
                    SocialRecoveryEventTrackerScreenId.PROTECTED_CUSTOMER_RECOVERY_RELATIONSHIP_NOT_ACTIVE,
                    RecoverySegment.SocRec.TrustedContact.Setup,
                    "When the customer opens up the push notification to endorse the recovery relationship, it isn't active."
                  )
                }
              }
            }
          }
        }
        LoadingBodyModel(id = null).asModalScreen()
      }

      is BenefactorInviteAccepted -> {
        BenefactorInviteAcceptedNotificationBodyModel(
          beneficiary = state.beneficiary,
          onDone = props.onBack
        ).asModalScreen()
      }

      is ProtectedCustomerInviteAccepted -> {
        ProtectedCustomerInviteAcceptedNotificationBodyModel(
          trustedContact = state.contact,
          onDone = props.onBack
        ).asModalScreen()
      }

      is RecoveryRelationshipDoesNotExist -> {
        ErrorFormBodyModel(
          title = "Recovery relationship is no longer active.",
          toolbar =
            ToolbarModel(
              leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(props.onBack)
            ),
          primaryButton =
            ButtonDataModel(
              text = "Close",
              onClick = props.onBack
            ),
          errorData = state.errorData,
          eventTrackerScreenId = state.eventTrackerScreenId
        ).asModalScreen()
      }
    }
  }

  private fun createRelationshipNotExistState(
    screenId: SocialRecoveryEventTrackerScreenId,
    segment: AppSegment,
    message: String,
  ): UiState {
    return RecoveryRelationshipDoesNotExist(
      screenId,
      ErrorData(
        segment,
        message,
        Error("Recovery relationship for notification is not active")
      )
    )
  }

  private sealed interface UiState {
    data object Loading : UiState

    data class BenefactorInviteAccepted(val beneficiary: TrustedContact) : UiState

    data class ProtectedCustomerInviteAccepted(val contact: TrustedContact) : UiState

    data class RecoveryRelationshipDoesNotExist(val eventTrackerScreenId: SocialRecoveryEventTrackerScreenId, val errorData: ErrorData) : UiState
  }
}
