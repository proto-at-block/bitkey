package build.wallet.statemachine.settings.full.device

import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon.BitkeyDevice3D
import build.wallet.statemachine.core.Icon.SmallIconRefresh
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.Button
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.DataHero
import build.wallet.statemachine.core.form.FormMainContentModel.Spacer
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.button.ButtonModel.Treatment.Secondary
import build.wallet.ui.model.button.ButtonModel.Treatment.TertiaryDestructive
import build.wallet.ui.model.button.ButtonModel.Treatment.TertiaryPrimaryNoUnderline
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize.XLarge
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Creates a [FormBodyModel] to display the device settings screen
 *
 * @param currentVersion - The current version of the hardware
 * @param updateVersion - The pending version the hardware can update to, null when there is none
 * @param modelNumber - The model number of the hardware
 * @param serialNumber - The serial number of the hardware
 * @param lastSyncDate - The last date the hardware was synced, formatted as a [String]
 * @param onUpdateVersion - Invoked once the update button is clicked, null when there is no update
 * @param onSyncDeviceInfo - Invoked once the sync button is clicked
 * @param onReplaceDevice - Invoked once the replace device button is clicked
 * @param onBack - Invoked once the back action is called
 */
fun DeviceSettingsFormBodyModel(
  currentVersion: String,
  updateVersion: String?,
  modelNumber: String,
  serialNumber: String,
  deviceCharge: String,
  lastSyncDate: String,
  replacementPending: String?,
  replaceDeviceEnabled: Boolean,
  onUpdateVersion: (() -> Unit)?,
  onSyncDeviceInfo: () -> Unit,
  onReplaceDevice: () -> Unit,
  onManageReplacement: (() -> Unit)?,
  onBack: () -> Unit,
) = FormBodyModel(
  id = SettingsEventTrackerScreenId.SETTINGS_DEVICE_INFO,
  onBack = onBack,
  toolbar =
    ToolbarModel(
      leadingAccessory = BackAccessory(onClick = onBack),
      middleAccessory = ToolbarMiddleAccessoryModel(title = "Bitkey Device")
    ),
  header = null,
  mainContentList =
    immutableListOf(
      DataList(
        hero =
          DataHero(
            image =
              IconModel(
                iconImage =
                  LocalImage(
                    icon = BitkeyDevice3D
                  ),
                iconSize = XLarge
              ),
            title =
              if (replacementPending != null) {
                "Replacement pending..."
              } else if (updateVersion != null) {
                "Update available"
              } else {
                "Up to date"
              },
            subtitle = replacementPending ?: currentVersion,
            button =
              replacementPending?.let {
                ButtonModel(
                  text = "Manage",
                  treatment = Secondary,
                  size = Footer,
                  onClick = Click.standardClick { onManageReplacement?.invoke() }
                )
              } ?: updateVersion?.let {
                ButtonModel(
                  text = "Update to $updateVersion",
                  treatment = Primary,
                  size = Footer,
                  onClick = Click.standardClick { onUpdateVersion?.invoke() }
                )
              }
          ),
        items =
          immutableListOf(
            DataList.Data(
              title = "Model name",
              sideText = "Bitkey",
              showBottomDivider = true
            ),
            DataList.Data(
              title = "Model number",
              sideText = modelNumber,
              showBottomDivider = true
            ),
            DataList.Data(
              title = "Serial number",
              sideText = serialNumber,
              showBottomDivider = true
            ),
            DataList.Data(
              title = "Firmware version",
              sideText = currentVersion,
              showBottomDivider = true
            ),
            DataList.Data(
              title = "Last known charge",
              sideText = deviceCharge,
              showBottomDivider = true
            ),
            DataList.Data(
              title = "Last sync",
              sideText = lastSyncDate
            )
          ),
        buttons =
          immutableListOf(
            ButtonModel(
              text = "Sync device info",
              treatment = TertiaryPrimaryNoUnderline,
              leadingIcon = SmallIconRefresh,
              size = Compact,
              onClick = Click.standardClick { onSyncDeviceInfo() }
            )
          )
      ),
      if (replacementPending == null) {
        Button(
          item =
            ButtonModel(
              text = "Replace device",
              treatment = TertiaryDestructive,
              size = Footer,
              onClick = Click.standardClick { onReplaceDevice() },
              isEnabled = replaceDeviceEnabled
            )
        )
      } else {
        Spacer()
      }
    ),
  primaryButton = null
)
