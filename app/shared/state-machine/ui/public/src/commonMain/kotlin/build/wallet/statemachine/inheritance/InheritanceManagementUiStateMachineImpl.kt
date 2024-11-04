package build.wallet.statemachine.inheritance

import androidx.compose.runtime.*
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
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.*
import build.wallet.ui.model.list.ListItemAccessory.Companion.drillIcon
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
        val benefactorsList =
          ListGroupModel(
            header = "Inheritance",
            items = benefactors.pcListItemModel(
              startClaim = {
                uiState = UiState.StartingClaim(it)
              }
            ),
            style = ListGroupStyle.CARD_GROUP_DIVIDER,
            footerButton =
              ButtonModel(
                text = "Accept an invite",
                treatment = ButtonModel.Treatment.Secondary,
                size = ButtonModel.Size.Footer,
                onClick = StandardClick {
                  uiState = UiState.AcceptingInvitation
                }
              )
          )

        val beneficiariesList = ListGroupModel(
          header = "Beneficiaries",
          items = beneficiaries.tcListItemModel(
            inheritanceClaims?.get().orEmpty()
          ),
          style = ListGroupStyle.CARD_GROUP_DIVIDER,
          footerButton =
            ButtonModel(
              text = "Add a beneficiary",
              treatment = ButtonModel.Treatment.Secondary,
              size = ButtonModel.Size.Footer,
              onClick = StandardClick {
                uiState = UiState.InvitingBeneficiary
              }
            )
        )

        ManagingInheritanceBodyModel(
          selectedTab = selectedTab,
          onBack = props.onBack,
          content = listOf(benefactorsList, beneficiariesList),
          onInviteClick = StandardClick {
            uiState = UiState.InvitingBeneficiary
          },
          onTabRowClick = { tab -> selectedTab = tab }
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

private fun List<TrustedContact>.tcListItemModel(pendingClaims: List<RelationshipId>) =
  map { contact ->
    ListItemModel(
      leadingAccessory =
        ListItemAccessory.CircularCharacterAccessory(
          character = contact.trustedContactAlias.alias
            .first()
            .uppercaseChar()
        ),
      title = contact.trustedContactAlias.alias,
      secondarySideText = "Claim pending".takeIf { contact.id in pendingClaims },
      sideTextTint = ListItemSideTextTint.SECONDARY,
      trailingAccessory = drillIcon(tint = IconTint.On30)
    )
  }.toImmutableList()

fun List<ProtectedCustomer>.pcListItemModel(startClaim: (RelationshipId) -> Unit) =
  this
    .map {
      ListItemModel(
        title = it.alias.alias,
        leadingAccessory =
          ListItemAccessory.CircularCharacterAccessory(
            character = it.alias.alias
              .first()
              .uppercaseChar()
          ),
        trailingAccessory = drillIcon(tint = IconTint.On30),
        onClick = {
          startClaim(RelationshipId(it.relationshipId))
        }
      )
    }.toImmutableList()

private sealed interface UiState {
  data object ManagingInheritance : UiState

  data object InvitingBeneficiary : UiState

  data object AcceptingInvitation : UiState

  data class StartingClaim(
    val relationshipId: RelationshipId,
  ) : UiState
}
