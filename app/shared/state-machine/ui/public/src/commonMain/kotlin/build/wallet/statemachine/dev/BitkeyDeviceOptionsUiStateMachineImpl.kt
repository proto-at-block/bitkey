package build.wallet.statemachine.dev

import androidx.compose.runtime.Composable
import build.wallet.platform.config.AppVariant
import build.wallet.statemachine.data.firmware.FirmwareData
import build.wallet.ui.model.button.ButtonModel.Companion.BitkeyInteractionButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemAccessory.ButtonAccessory
import build.wallet.ui.model.list.ListItemModel
import kotlinx.collections.immutable.toImmutableList

class BitkeyDeviceOptionsUiStateMachineImpl(
  private val appVariant: AppVariant,
) : BitkeyDeviceOptionsUiStateMachine {
  @Composable
  override fun model(props: BitkeyDeviceOptionsUiProps): ListGroupModel {
    // Only show button to FWUP if there is a pending update
    // and we are not in a Customer build
    val firmwareUpdateItem =
      when (appVariant) {
        AppVariant.Customer -> null
        else -> {
          when (val firmwareUpdateState = props.firmwareData.firmwareUpdateState) {
            is FirmwareData.FirmwareUpdateState.UpToDate -> null
            is FirmwareData.FirmwareUpdateState.PendingUpdate ->
              ListItemModel(
                title = "Firmware Update",
                trailingAccessory =
                  ButtonAccessory(
                    model =
                      BitkeyInteractionButtonModel(
                        text = "Update",
                        isLoading = false,
                        size = Compact,
                        onClick =
                          {
                            props.onFirmwareUpdateClick(firmwareUpdateState)
                          }
                      )
                  )
              )
          }
        }
      }

    return ListGroupModel(
      style = ListGroupStyle.DIVIDER,
      items =
        listOfNotNull(
          ListItemModel(
            title = "Firmware Metadata",
            trailingAccessory = ListItemAccessory.drillIcon(),
            onClick = props.onFirmwareMetadataClick
          ),
          firmwareUpdateItem,
          ListItemModel(
            title = "Wipe Bitkey",
            secondaryText = "Only use this if instructed to by a Bitkey team member. You may lose access to your money.",
            trailingAccessory =
              ButtonAccessory(
                model =
                  BitkeyInteractionButtonModel(
                    text = "Wipe",
                    size = Compact,
                    onClick = props.onWipeBitkeyClick
                  )
              )
          )
        ).toImmutableList()
    )
  }
}
