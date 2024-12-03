package build.wallet.statemachine.inheritance

import androidx.compose.runtime.*
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.bitkey.relationships.TrustedContact
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.inheritance.InheritanceService
import build.wallet.statemachine.core.*
import build.wallet.statemachine.inheritance.claims.start.StartClaimUiStateMachine
import build.wallet.statemachine.inheritance.claims.start.StartClaimUiStateMachineProps
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiProps
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiStateMachine
import build.wallet.ui.model.Click
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.*
import com.github.michaelbull.result.get
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

class InheritanceManagementUiStateMachineImpl(
  private val inviteBeneficiaryUiStateMachine: InviteBeneficiaryUiStateMachine,
  private val trustedContactEnrollmentUiStateMachine: TrustedContactEnrollmentUiStateMachine,
  private val inheritanceService: InheritanceService,
  private val startClaimUiStateMachine: StartClaimUiStateMachine,
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
      inheritanceService.pendingClaims
    }.collectAsState()

    return when (uiState) {
      // TODO W-9135 W-9383 add inheritance management UI to design spec
      is UiState.StartingClaim -> startClaimUiStateMachine.model(
        StartClaimUiStateMachineProps(
          relationshipId = (uiState as UiState.StartingClaim).relationshipId,
          onExit = {
            uiState = UiState.ManagingInheritance
          }
        )
      )

      UiState.ManagingInheritance -> {
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
            inheritanceClaims = inheritanceClaims?.get().orEmpty()
          ),
          benefactors = BenefactorListModel(
            benefactors = benefactors,
            onStartClaimClick = {
              uiState = UiState.StartingClaim(it)
            }
          )
        ).asRootScreen()
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
  onStartClaimClick: (RelationshipId) -> Unit,
): ListGroupModel {
  return ListGroupModel(
    items = benefactors.pcListItemModel(
      startClaim = onStartClaimClick
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

private fun List<ProtectedCustomer>.pcListItemModel(startClaim: (RelationshipId) -> Unit) =
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
            startClaim(RelationshipId(it.relationshipId))
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

  data class StartingClaim(
    val relationshipId: RelationshipId,
  ) : UiState
}
