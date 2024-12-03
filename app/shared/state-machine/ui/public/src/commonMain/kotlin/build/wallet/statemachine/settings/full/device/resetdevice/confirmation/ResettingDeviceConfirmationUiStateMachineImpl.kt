package build.wallet.statemachine.settings.full.device.resetdevice.confirmation

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.compose.collections.buildImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.logging.*
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.form.*
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.settings.full.device.resetdevice.ResettingDeviceEventTrackerScreenId
import build.wallet.statemachine.settings.full.device.resetdevice.confirmation.ResettingDeviceConfirmationUiState.ConfirmationScreen
import build.wallet.statemachine.settings.full.device.resetdevice.confirmation.ResettingDeviceConfirmationUiState.ResettingDevice
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Companion.BitkeyInteractionButtonModel
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.list.*
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.get
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.firstOrNull

class ResettingDeviceConfirmationUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
) : ResettingDeviceConfirmationUiStateMachine {
  private val confirmationMessages = arrayOf(
    "Resetting my device means that I will not be able to use it to verify any future transfers or security changes.",
    "Resetting my device means that I will not be able to use it to set up my wallet on a new phone."
  )

  @Composable
  override fun model(props: ResettingDeviceConfirmationProps): ScreenModel {
    var uiState: ResettingDeviceConfirmationUiState by remember {
      mutableStateOf(
        ConfirmationScreen()
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

    when (val state = uiState) {
      is ConfirmationScreen -> {
        val allMessagesChecked = confirmationMessageStates.all {
          it is ResettingDeviceConfirmationState.Completed
        }

        if (allMessagesChecked && !state.isShowingScanAndResetSheet) {
          uiState = state.copy(
            isShowingConfirmationWarning = false
          )
        }

        val onConfirmResetDevice: () -> Unit = if (allMessagesChecked) {
          {
            uiState = state.copy(
              isShowingScanAndResetSheet = true
            )
          }
        } else {
          {
            uiState = state.copy(
              isShowingConfirmationWarning = true
            )
          }
        }

        return ResettingDeviceConfirmationModel(
          onBack = props.onBack,
          onConfirmResetDevice = onConfirmResetDevice,
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
          isShowingConfirmationWarning = state.isShowingConfirmationWarning,
          bottomSheetModel =
            if (state.isShowingScanAndResetSheet) {
              ScanAndResetConfirmationSheet(
                onBack = { uiState = state.copy(isShowingScanAndResetSheet = false) },
                onConfirmResetDevice = {
                  uiState = ResettingDevice
                }
              )
            } else {
              null
            }
        )
      }

      is ResettingDevice -> {
        return ResetDeviceModel(
          onSuccess = props.onResetDevice,
          onCancel = {
            uiState = ConfirmationScreen()
          },
          isDevicePaired = props.isDevicePaired,
          isHardwareFake = props.isHardwareFake
        )
      }
    }
  }

  @Composable
  private fun ResettingDeviceConfirmationModel(
    onBack: () -> Unit,
    onConfirmResetDevice: () -> Unit,
    messageItemModels: ImmutableList<ResettingDeviceConfirmationItemModel>,
    isShowingConfirmationWarning: Boolean,
    bottomSheetModel: SheetModel? = null,
  ): ScreenModel {
    val mainContentList = buildImmutableList {
      add(
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
      )
      if (isShowingConfirmationWarning) {
        add(
          FormMainContentModel.Callout(
            item = CalloutModel(
              title = "To reset your device, please confirm and acknowledge the messages above.",
              subtitle = null,
              treatment = CalloutModel.Treatment.Warning
            )
          )
        )
      }
    }

    return ScreenModel(
      body = ResettingDeviceConfirmationBodyModel(
        onBack = onBack,
        onConfirmResetDevice = onConfirmResetDevice,
        mainContentList = mainContentList
      ),
      presentationStyle = ScreenPresentationStyle.FullScreen,
      bottomSheetModel = bottomSheetModel
    )
  }

  private data class ResettingDeviceConfirmationBodyModel(
    override val onBack: () -> Unit,
    override val mainContentList: ImmutableList<FormMainContentModel>,
    val onConfirmResetDevice: () -> Unit,
  ) : FormBodyModel(
      id = ResettingDeviceEventTrackerScreenId.RESET_DEVICE_CONFIRMATION,
      onBack = onBack,
      toolbar = ToolbarModel(
        leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory(onBack)
      ),
      header = FormHeaderModel(
        headline = "Confirm to continue",
        subline = "Please read and agree to the following before you continue."
      ),
      mainContentList = mainContentList,
      primaryButton = ButtonModel(
        text = "Reset device",
        isEnabled = true,
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.Secondary,
        onClick = StandardClick(onConfirmResetDevice)
      )
    )

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

  @Composable
  private fun ScanAndResetConfirmationSheet(
    onBack: () -> Unit,
    onConfirmResetDevice: () -> Unit,
  ): SheetModel {
    return SheetModel(
      size = SheetSize.DEFAULT,
      dragIndicatorVisible = false,
      onClosed = onBack,
      body = ScanAndResetConfirmationSheetBodyModel(
        onBack = onBack,
        onConfirmResetDevice = onConfirmResetDevice
      )
    )
  }

  private data class ScanAndResetConfirmationSheetBodyModel(
    override val onBack: () -> Unit,
    val onConfirmResetDevice: () -> Unit,
  ) : FormBodyModel(
      id = ResettingDeviceEventTrackerScreenId.SCAN_AND_RESET_SHEET,
      onBack = onBack,
      toolbar = null,
      header = FormHeaderModel(
        headline = "Scan your device to reset it",
        subline = "Hold your unlocked device behind your phone and start scanning to reset it."
      ),
      mainContentList = immutableListOf(
        FormMainContentModel.Callout(
          item = CalloutModel(
            title = "Resetting cannot be undone",
            subtitle = StringModel("This will reset the device to its blank, factory state"),
            treatment = CalloutModel.Treatment.Danger
          )
        )
      ),
      primaryButton = BitkeyInteractionButtonModel(
        text = "Scan and Reset",
        treatment = ButtonModel.Treatment.PrimaryDestructive,
        size = ButtonModel.Size.Footer,
        onClick = StandardClick { onConfirmResetDevice() }
      ),
      secondaryButton = ButtonModel(
        text = "Cancel",
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.Secondary,
        onClick = StandardClick(onBack)
      ),
      renderContext = RenderContext.Sheet
    )

  @Composable
  private fun ResetDeviceModel(
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    isDevicePaired: Boolean,
    isHardwareFake: Boolean,
  ): ScreenModel {
    return nfcSessionUIStateMachine.model(
      NfcSessionUIStateMachineProps(
        session = { session, commands ->
          commands.wipeDevice(session)
        },
        onSuccess = {
          val firmwareSerial = firmwareDeviceInfoDao.deviceInfo().firstOrNull()?.get()?.serial ?: "failed to retrieve serial number"
          logDebug { "Bitkey reset successfully with serial number: $firmwareSerial" }
          if (isDevicePaired) {
            firmwareDeviceInfoDao.clear()
          }
          onSuccess()
        },
        onCancel = onCancel,
        isHardwareFake = isHardwareFake,
        screenPresentationStyle = ScreenPresentationStyle.Modal,
        shouldLock = false,
        eventTrackerContext = NfcEventTrackerScreenIdContext.WIPE_DEVICE
      )
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
    val isShowingConfirmationWarning: Boolean = false,
    val isShowingScanAndResetSheet: Boolean = false,
  ) : ResettingDeviceConfirmationUiState

  /**
   * Resetting the device
   */
  data object ResettingDevice : ResettingDeviceConfirmationUiState
}
