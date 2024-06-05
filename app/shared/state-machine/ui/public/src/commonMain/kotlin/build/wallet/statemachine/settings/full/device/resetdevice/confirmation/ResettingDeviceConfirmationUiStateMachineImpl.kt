package build.wallet.statemachine.settings.full.device.resetdevice.confirmation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.settings.full.device.resetdevice.ResettingDeviceEventTrackerScreenId
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemTreatment
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

class ResettingDeviceConfirmationUiStateMachineImpl : ResettingDeviceConfirmationUiStateMachine {
  private val confirmationMessages = arrayOf(
    "Resetting my device means that I will not be able to use it to verify any future transfers or security changes.",
    "Resetting my device means that I will not be able to use it to set up my wallet on a new phone."
  )

  @Composable
  override fun model(props: ResettingDeviceConfirmationProps): ScreenModel {
    val uiState: ResettingDeviceConfirmationUiState by remember {
      mutableStateOf(
        ResettingDeviceConfirmationUiState.ConfirmationScreen()
      )
    }
    // List to manage the states of the checkboxes
    var confirmationMessageStates by remember {
      mutableStateOf<ImmutableList<ResettingDeviceConfirmationState>>(
        List(confirmationMessages.size) {
          ResettingDeviceConfirmationState.NotCompleted
        }.toImmutableList()
      )
    }

    when (uiState) {
      is ResettingDeviceConfirmationUiState.ConfirmationScreen ->
        return ResettingDeviceConfirmationModel(
          onBack = props.onBack,
          onConfirmResetDevice = props.onConfirmResetDevice,
          messageItemModels = confirmationMessages.mapIndexed { index, message ->
            ResettingDeviceConfirmationItemModel(
              state = confirmationMessageStates[index],
              title = message,
              onClick = {
                confirmationMessageStates = confirmationMessageStates.toMutableList().apply {
                  this[index] = when (this[index]) {
                    is ResettingDeviceConfirmationState.Completed -> ResettingDeviceConfirmationState.NotCompleted
                    is ResettingDeviceConfirmationState.NotCompleted -> ResettingDeviceConfirmationState.Completed
                  }
                }.toImmutableList()
              }
            )
          }.toImmutableList(),
          isButtonEnabled = confirmationMessageStates.all {
            it is ResettingDeviceConfirmationState.Completed
          }
        )
    }
  }

  @Composable
  private fun ResettingDeviceConfirmationModel(
    onBack: () -> Unit,
    onConfirmResetDevice: () -> Unit,
    messageItemModels: ImmutableList<ResettingDeviceConfirmationItemModel>,
    isButtonEnabled: Boolean,
    bottomSheetModel: SheetModel? = null,
  ): ScreenModel {
    return ScreenModel(
      body = FormBodyModel(
        id = ResettingDeviceEventTrackerScreenId.RESET_DEVICE_CONFIRMATION,
        onBack = onBack,
        toolbar = ToolbarModel(
          leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory(onBack)
        ),
        header = FormHeaderModel(
          headline = "Confirm to continue",
          subline = "Please read and agree to the following before you continue."
        ),
        mainContentList = immutableListOf(
          FormMainContentModel.ListGroup(
            listGroupModel = ListGroupModel(
              items = messageItemModels.map { itemModel ->
                createListItem(
                  itemModel = itemModel,
                  title = itemModel.title
                )
              }.toImmutableList(),
              style = ListGroupStyle.DIVIDER
            )
          )
        ),
        primaryButton = ButtonModel(
          text = "Reset device",
          isEnabled = isButtonEnabled,
          size = ButtonModel.Size.Footer,
          treatment = ButtonModel.Treatment.Secondary,
          onClick = StandardClick { onConfirmResetDevice() }
        )
      ),
      presentationStyle = ScreenPresentationStyle.FullScreen,
      bottomSheetModel = bottomSheetModel
    )
  }

  private fun createListItem(
    itemModel: ResettingDeviceConfirmationItemModel,
    title: String,
  ) = with(itemModel) {
    ListItemModel(
      leadingAccessory = state.leadingAccessory(
        onClick = onClick
      ),
      title = title,
      treatment = ListItemTreatment.PRIMARY
    )
  }
}

private fun ResettingDeviceConfirmationState.leadingAccessory(
  onClick: () -> Unit,
): ListItemAccessory =
  when (this) {
    ResettingDeviceConfirmationState.NotCompleted -> ListItemAccessory.IconAccessory(
      model = IconModel(
        icon = Icon.SmallIconCheckbox,
        iconSize = IconSize.Small
      ),
      onClick = onClick
    )

    ResettingDeviceConfirmationState.Completed -> ListItemAccessory.IconAccessory(
      model = IconModel(
        icon = Icon.SmallIconCheckboxSelected,
        iconSize = IconSize.Small
      ),
      onClick = onClick
    )
  }

private sealed interface ResettingDeviceConfirmationUiState {
  /**
   * Viewing the reset device intro screen
   */
  data class ConfirmationScreen(
    val isShowingScanAndResetSheet: Boolean = false,
  ) : ResettingDeviceConfirmationUiState
}
