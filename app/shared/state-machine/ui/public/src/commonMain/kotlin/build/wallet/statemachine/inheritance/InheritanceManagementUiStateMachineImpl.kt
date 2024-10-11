package build.wallet.statemachine.inheritance

import androidx.compose.runtime.*
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.bitkey.relationships.TrustedContact
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.inheritance.InheritanceService
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.RetreatStyle
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.formBodyModel
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiProps
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiStateMachine
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.*
import build.wallet.ui.model.list.ListItemAccessory.Companion.drillIcon
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

class InheritanceManagementUiStateMachineImpl(
  private val inviteBeneficiaryUiStateMachine: InviteBeneficiaryUiStateMachine,
  private val trustedContactEnrollmentUiStateMachine: TrustedContactEnrollmentUiStateMachine,
  private val inheritanceService: InheritanceService,
) : InheritanceManagementUiStateMachine {
  @Composable
  override fun model(props: InheritanceManagementUiProps): ScreenModel {
    var uiState: UiState by remember { mutableStateOf(UiState.ManagingInheritance) }

    val inheritanceRelationships by remember {
      inheritanceService.inheritanceRelationships
    }.collectAsState(null)

    val beneficiaries: ImmutableList<TrustedContact> = remember(inheritanceRelationships) {
      inheritanceRelationships?.let {
        (it.invitations + it.endorsedTrustedContacts + it.unendorsedTrustedContacts).toImmutableList()
      } ?: emptyImmutableList()
    }

    val benefactors = remember(inheritanceRelationships) {
      inheritanceRelationships?.protectedCustomers ?: emptyImmutableList()
    }

    return when (uiState) {
      // TODO W-9135 W-9383 add inheritance management UI to design spec
      UiState.ManagingInheritance -> formBodyModel(
        id = null,
        onBack = props.onBack,
        header = FormHeaderModel(
          headline = "Inheritance",
          subline = "Manage your beneficiaries and inheritance claims."
        ),
        toolbar = ToolbarModel(
          leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory { props.onBack() }
        ),
        mainContentList = immutableListOf(
          FormMainContentModel.ListGroup(
            ListGroupModel(
              header = "Inheritance",
              items = benefactors.pcListItemModel(),
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
          ),
          FormMainContentModel.ListGroup(
            ListGroupModel(
              header = "Beneficiaries",
              items = beneficiaries.tcListItemModel(),
              style = ListGroupStyle.CARD_GROUP_DIVIDER
            )
          )
        ),
        primaryButton = ButtonModel(
          text = "Invite",
          onClick = StandardClick {
            uiState = UiState.InvitingBeneficiary
          },
          size = ButtonModel.Size.Footer
        )
      ).asRootScreen()
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

private fun List<TrustedContact>.tcListItemModel() =
  map { contact ->
    ListItemModel(
      leadingAccessory =
        ListItemAccessory.CircularCharacterAccessory(
          character = contact.trustedContactAlias.alias.first().uppercaseChar()
        ),
      title = contact.trustedContactAlias.alias,
      sideTextTint = ListItemSideTextTint.SECONDARY,
      trailingAccessory = drillIcon(tint = IconTint.On30)
    )
  }.toImmutableList()

fun List<ProtectedCustomer>.pcListItemModel() =
  this.map {
    ListItemModel(
      title = it.alias.alias,
      leadingAccessory =
        ListItemAccessory.CircularCharacterAccessory(
          character = it.alias.alias.first().uppercaseChar()
        ),
      trailingAccessory = drillIcon(tint = IconTint.On30)
    )
  }.toImmutableList()

private sealed interface UiState {
  data object ManagingInheritance : UiState

  data object InvitingBeneficiary : UiState

  data object AcceptingInvitation : UiState
}
