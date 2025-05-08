package build.wallet.statemachine.settings.full.device.wipedevice.confirmation

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.compose.collections.buildImmutableList
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.logging.logDebug
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceEventTrackerScreenId
import build.wallet.statemachine.settings.full.device.wipedevice.confirmation.WipingDeviceConfirmationUiState.ConfirmationScreen
import build.wallet.statemachine.settings.full.device.wipedevice.confirmation.WipingDeviceConfirmationUiState.WipingDevice
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
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

@BitkeyInject(ActivityScope::class)
class WipingDeviceConfirmationUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
) : WipingDeviceConfirmationUiStateMachine {
  private val confirmationMessages = arrayOf(
    "This device can no longer be used to access the funds in my Bitkey wallet.",
    "This device can no longer be used to recover access to my Bitkey wallet if I lose my phone."
  )

  @Composable
  override fun model(props: WipingDeviceConfirmationProps): ScreenModel {
    var uiState: WipingDeviceConfirmationUiState by remember {
      mutableStateOf(
        ConfirmationScreen()
      )
    }
    // List to manage the states of the checkboxes
    var confirmationMessageStates by remember {
      mutableStateOf<ImmutableList<WipingDeviceConfirmationState>>(
        List(confirmationMessages.size) {
          WipingDeviceConfirmationState.NotCompleted
        }.toImmutableList()
      )
    }

    when (val state = uiState) {
      is ConfirmationScreen -> {
        val allMessagesChecked = confirmationMessageStates.all {
          it is WipingDeviceConfirmationState.Completed
        }

        if (allMessagesChecked && !state.isShowingScanAndWipeSheet) {
          uiState = state.copy(
            isShowingConfirmationWarning = false
          )
        }

        val onConfirmWipeDevice: () -> Unit = if (allMessagesChecked) {
          {
            uiState = state.copy(
              isShowingScanAndWipeSheet = true
            )
          }
        } else {
          {
            uiState = state.copy(
              isShowingConfirmationWarning = true
            )
          }
        }

        return WipingDeviceConfirmationModel(
          onBack = props.onBack,
          onConfirmWipeDevice = onConfirmWipeDevice,
          messageItemModels = confirmationMessages.mapIndexed { index, message ->
            WipingDeviceConfirmationItemModel(
              state = confirmationMessageStates[index],
              title = message,
              onClick = {
                confirmationMessageStates = confirmationMessageStates.toMutableList().apply {
                  this[index] = when (this[index]) {
                    is WipingDeviceConfirmationState.Completed -> WipingDeviceConfirmationState.NotCompleted
                    is WipingDeviceConfirmationState.NotCompleted -> WipingDeviceConfirmationState.Completed
                  }
                }.toImmutableList()
              }
            )
          }.toImmutableList(),
          isShowingConfirmationWarning = state.isShowingConfirmationWarning,
          bottomSheetModel =
            if (state.isShowingScanAndWipeSheet) {
              ScanAndWipeConfirmationSheet(
                onBack = { uiState = state.copy(isShowingScanAndWipeSheet = false) },
                onConfirmWipeDevice = {
                  uiState = WipingDevice
                }
              )
            } else {
              null
            }
        )
      }

      is WipingDevice -> {
        return WipeDeviceModel(
          onSuccess = props.onWipeDevice,
          onCancel = {
            uiState = ConfirmationScreen()
          },
          isDevicePaired = props.isDevicePaired
        )
      }
    }
  }

  @Composable
  private fun WipingDeviceConfirmationModel(
    onBack: () -> Unit,
    onConfirmWipeDevice: () -> Unit,
    messageItemModels: ImmutableList<WipingDeviceConfirmationItemModel>,
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
              title = "To wipe your device, please confirm and acknowledge the messages above.",
              subtitle = null,
              treatment = CalloutModel.Treatment.Warning
            )
          )
        )
      }
    }

    return ScreenModel(
      body = WipingDeviceConfirmationBodyModel(
        onBack = onBack,
        onConfirmWipeDevice = onConfirmWipeDevice,
        mainContentList = mainContentList
      ),
      bottomSheetModel = bottomSheetModel
    )
  }

  private data class WipingDeviceConfirmationBodyModel(
    override val onBack: () -> Unit,
    override val mainContentList: ImmutableList<FormMainContentModel>,
    val onConfirmWipeDevice: () -> Unit,
  ) : FormBodyModel(
      id = WipingDeviceEventTrackerScreenId.RESET_DEVICE_CONFIRMATION,
      onBack = onBack,
      toolbar = ToolbarModel(
        leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory(onBack)
      ),
      header = FormHeaderModel(
        headline = "Confirm to continue",
        subline = "I understand that:"
      ),
      mainContentList = mainContentList,
      primaryButton = ButtonModel(
        text = "Wipe device",
        isEnabled = true,
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.Secondary,
        onClick = StandardClick(onConfirmWipeDevice)
      )
    )

  private fun createListItem(
    itemModel: WipingDeviceConfirmationItemModel,
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
  private fun ScanAndWipeConfirmationSheet(
    onBack: () -> Unit,
    onConfirmWipeDevice: () -> Unit,
  ): SheetModel {
    return SheetModel(
      size = SheetSize.DEFAULT,
      onClosed = onBack,
      body = ScanAndWipeConfirmationSheetBodyModel(
        onBack = onBack,
        onConfirmWipeDevice = onConfirmWipeDevice
      )
    )
  }

  @Composable
  private fun WipeDeviceModel(
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    isDevicePaired: Boolean,
  ): ScreenModel {
    return nfcSessionUIStateMachine.model(
      NfcSessionUIStateMachineProps(
        session = { session, commands ->
          commands.wipeDevice(session)
        },
        onSuccess = {
          val firmwareSerial = firmwareDeviceInfoDao.deviceInfo().firstOrNull()?.get()?.serial ?: "failed to retrieve serial number"
          logDebug { "Bitkey wipe successfully with serial number: $firmwareSerial" }
          if (isDevicePaired) {
            firmwareDeviceInfoDao.clear()
          }
          onSuccess()
        },
        onCancel = onCancel,
        hardwareVerification = NotRequired,
        screenPresentationStyle = ScreenPresentationStyle.Modal,
        shouldLock = false,
        eventTrackerContext = NfcEventTrackerScreenIdContext.WIPE_DEVICE
      )
    )
  }
}

private fun WipingDeviceConfirmationState.leadingAccessory(
  onClick: () -> Unit,
): ListItemAccessory =
  when (this) {
    WipingDeviceConfirmationState.NotCompleted -> ListItemAccessory.IconAccessory(
      model = IconModel(
        icon = Icon.SmallIconCheckbox,
        iconSize = IconSize.Small
      ),
      onClick = onClick
    )

    WipingDeviceConfirmationState.Completed -> ListItemAccessory.IconAccessory(
      model = IconModel(
        icon = Icon.SmallIconCheckboxSelected,
        iconSize = IconSize.Small
      ),
      onClick = onClick
    )
  }

private sealed interface WipingDeviceConfirmationUiState {
  /**
   * Viewing the wipe device intro screen
   */
  data class ConfirmationScreen(
    val isShowingConfirmationWarning: Boolean = false,
    val isShowingScanAndWipeSheet: Boolean = false,
  ) : WipingDeviceConfirmationUiState

  /**
   * Wiping the device
   */
  data object WipingDevice : WipingDeviceConfirmationUiState
}
