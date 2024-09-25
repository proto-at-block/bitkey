package build.wallet.statemachine.inheritance

import androidx.compose.runtime.*
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

class InheritanceManagementUiStateMachineImpl(
  private val inviteBeneficiaryUiStateMachine: InviteBeneficiaryUiStateMachine,
) : InheritanceManagementUiStateMachine {
  @Composable
  override fun model(props: InheritanceManagementUiProps): ScreenModel {
    var uiState: UiState by remember { mutableStateOf(UiState.ManagingInheritance) }

    return when (uiState) {
      // TODO W-9135 W-9383 add inheritance management UI to design spec
      UiState.ManagingInheritance -> FormBodyModel(
        id = null,
        onBack = props.onBack,
        header = FormHeaderModel(
          headline = "Inheritance",
          subline = "Manage your beneficiaries and inheritance claims."
        ),
        toolbar = ToolbarModel(
          leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory { props.onBack() }
        ),
        primaryButton = ButtonModel(
          text = "Invite",
          onClick = StandardClick {
            uiState = UiState.InvitingBeneficiary
          },
          size = ButtonModel.Size.Footer
        ),
        secondaryButton = ButtonModel(
          text = "Accept",
          onClick = StandardClick {
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
    }
  }
}

private sealed interface UiState {
  data object ManagingInheritance : UiState

  data object InvitingBeneficiary : UiState
}
