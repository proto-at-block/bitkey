package build.wallet.statemachine.inheritance

import androidx.compose.runtime.*
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.bitkey.relationships.TrustedContact
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.inheritance.InheritanceService
import build.wallet.statemachine.core.*
import build.wallet.statemachine.inheritance.claims.complete.CompleteInheritanceClaimUiStateMachine
import build.wallet.statemachine.inheritance.claims.complete.CompleteInheritanceClaimUiStateMachineProps
import build.wallet.statemachine.inheritance.claims.start.StartClaimUiStateMachine
import build.wallet.statemachine.inheritance.claims.start.StartClaimUiStateMachineProps
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiProps
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiStateMachine
import build.wallet.ui.model.Click
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@BitkeyInject(ActivityScope::class)
class InheritanceManagementUiStateMachineImpl(
  private val inviteBeneficiaryUiStateMachine: InviteBeneficiaryUiStateMachine,
  private val trustedContactEnrollmentUiStateMachine: TrustedContactEnrollmentUiStateMachine,
  private val inheritanceService: InheritanceService,
  private val startClaimUiStateMachine: StartClaimUiStateMachine,
  private val completeClaimUiStateMachine: CompleteInheritanceClaimUiStateMachine,
) : InheritanceManagementUiStateMachine {
  @Composable
  override fun model(props: InheritanceManagementUiProps): ScreenModel {
    var uiState: UiState by remember { mutableStateOf(UiState.ManagingInheritance) }
    var selectedTab by remember { mutableStateOf(ManagingInheritanceTab.Inheritance) }

    val inheritanceRelationships by remember {
      inheritanceService.inheritanceRelationships
    }.collectAsState(null)

    val beneficiaries: ImmutableList<TrustedContact> = remember(inheritanceRelationships) {
      inheritanceRelationships?.let {
        (it.invitations + it.endorsedTrustedContacts + it.unendorsedTrustedContacts)
          .toImmutableList()
      } ?: emptyImmutableList()
    }

    val benefactors = remember(inheritanceRelationships) {
      inheritanceRelationships?.protectedCustomers ?: emptyImmutableList()
    }

    val inheritanceClaims by remember {
      inheritanceService.relationshipsWithPendingClaim
    }.collectAsState(emptyList())

    val startable by remember {
      inheritanceService.relationshipsWithNoActiveClaims
    }.collectAsState(emptyList())

    val completable by remember {
      inheritanceService.relationshipsWithCompletableClaim
    }.collectAsState(emptyList())

    val cancelable by remember {
      inheritanceService.relationshipsWithCancelableClaim
    }.collectAsState(emptyList())

    return when (val currentState = uiState) {
      // TODO W-9135 W-9383 add inheritance management UI to design spec
      is UiState.StartingClaim -> startClaimUiStateMachine.model(
        StartClaimUiStateMachineProps(
          relationshipId = currentState.relationshipId,
          onExit = {
            uiState = UiState.ManagingInheritance
          }
        )
      )
      is UiState.CompletingClaim -> completeClaimUiStateMachine.model(
        CompleteInheritanceClaimUiStateMachineProps(
          account = props.account,
          relationshipId = currentState.relationshipId,
          onExit = {
            uiState = UiState.ManagingInheritance
          }
        )
      )

      is UiState.ManageBenefactorRelationship,
      UiState.ManagingInheritance,
      -> {
        ManagingInheritanceBodyModel(
          selectedTab = selectedTab,
          onBack = props.onBack,
          onInviteClick = StandardClick {
            uiState = UiState.InvitingBeneficiary
          },
          onTabRowClick = { tab -> selectedTab = tab },
          onAcceptInvitation = { uiState = UiState.AcceptingInvitation },
          hasPendingBeneficiaries = beneficiaries.any { it is Invitation },
          beneficiaries = BeneficiaryListModel(
            beneficiaries = beneficiaries,
            inheritanceClaims = inheritanceClaims
          ),
          benefactors = BenefactorListModel(
            benefactors = benefactors,
            onManageClick = {
              uiState = UiState.ManageBenefactorRelationship(it)
            }
          )
        ).let {
          if (currentState is UiState.ManageBenefactorRelationship) {
            ScreenModel(
              body = it,
              bottomSheetModel = SheetModel(
                body = ManageBenefactorBodyModel(
                  onClose = { uiState = UiState.ManagingInheritance },
                  onRemoveBenefactor = {
                    // W-9966: Implement Removal
                  },
                  claimControls = when (currentState.relationshipId) {
                    in startable -> ManageBenefactorBodyModel.ClaimControls.Start(
                      onClick = {
                        uiState = UiState.StartingClaim(currentState.relationshipId)
                      }
                    )
                    in completable -> ManageBenefactorBodyModel.ClaimControls.Complete(
                      onClick = {
                        uiState = UiState.CompletingClaim(currentState.relationshipId)
                      }
                    )
                    in cancelable -> ManageBenefactorBodyModel.ClaimControls.Cancel(
                      onClick = {
                        // W-9977: Implement Cancellation
                      }
                    )
                    else -> ManageBenefactorBodyModel.ClaimControls.None
                  }
                ),
                onClosed = { uiState = UiState.ManagingInheritance }
              )
            )
          } else {
            it.asRootScreen()
          }
        }
      }
      UiState.InvitingBeneficiary -> inviteBeneficiaryUiStateMachine.model(
        InviteBeneficiaryUiProps(
          account = props.account,
          onExit = {
            uiState = UiState.ManagingInheritance
          }
        )
      )
      UiState.AcceptingInvitation -> trustedContactEnrollmentUiStateMachine.model(
        TrustedContactEnrollmentUiProps(
          retreat = Retreat(
            style = RetreatStyle.Close,
            onRetreat = { uiState = UiState.ManagingInheritance }
          ),
          account = props.account,
          inviteCode = null,
          screenPresentationStyle = ScreenPresentationStyle.Modal,
          onDone = {
            uiState = UiState.ManagingInheritance
          }
        )
      )
    }
  }
}

@Stable
fun BenefactorListModel(
  benefactors: List<ProtectedCustomer>,
  onManageClick: (RelationshipId) -> Unit,
): ListGroupModel {
  return ListGroupModel(
    items = benefactors.pcListItemModel(
      onManageClick = onManageClick
    ),
    style = ListGroupStyle.DIVIDER
  )
}

@Stable
fun BeneficiaryListModel(
  beneficiaries: ImmutableList<TrustedContact>,
  inheritanceClaims: List<RelationshipId>,
): ListGroupModel {
  return ListGroupModel(
    items = beneficiaries.tcListItemModel(inheritanceClaims),
    style = ListGroupStyle.DIVIDER
  )
}

private fun List<TrustedContact>.tcListItemModel(pendingClaims: List<RelationshipId>) =
  map { contact ->
    ListItemModel(
      title = contact.trustedContactAlias.alias,
      leadingAccessory = ListItemAccessory.ContactAvatarAccessory(
        name = contact.trustedContactAlias.alias
      ),
      secondaryText = when {
        contact.id in pendingClaims -> "Claim pending"
        contact is Invitation -> "Pending"
        else -> "Active"
      },
      sideTextTint = ListItemSideTextTint.SECONDARY,
      trailingAccessory = ManageContactButton(
        onClick = StandardClick {
        }
      )
    )
  }.toImmutableList()

private fun List<ProtectedCustomer>.pcListItemModel(onManageClick: (RelationshipId) -> Unit) =
  this
    .map {
      ListItemModel(
        title = it.alias.alias,
        leadingAccessory = ListItemAccessory.ContactAvatarAccessory(
          name = it.alias.alias
        ),
        secondaryText = "Active",
        trailingAccessory = ManageContactButton(
          onClick = StandardClick {
            onManageClick(RelationshipId(it.relationshipId))
          }
        )
      )
    }.toImmutableList()

@Stable
private fun ManageContactButton(onClick: Click): ListItemAccessory {
  return ListItemAccessory.ButtonAccessory(
    model = ButtonModel(
      text = "Manage",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Short,
      onClick = onClick
    )
  )
}

private sealed interface UiState {
  data object ManagingInheritance : UiState

  data object InvitingBeneficiary : UiState

  data object AcceptingInvitation : UiState

  data class ManageBenefactorRelationship(
    val relationshipId: RelationshipId,
  ) : UiState

  data class StartingClaim(
    val relationshipId: RelationshipId,
  ) : UiState

  data class CompletingClaim(
    val relationshipId: RelationshipId,
  ) : UiState
}
