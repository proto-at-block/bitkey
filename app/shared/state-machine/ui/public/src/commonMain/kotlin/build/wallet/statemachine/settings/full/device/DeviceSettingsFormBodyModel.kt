package build.wallet.statemachine.settings.full.device

import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.statemachine.core.Icon.BitkeyDevice3D
import build.wallet.statemachine.core.Icon.SmallIconSync
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.Button
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.DataHero
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.form.FormMainContentModel.Spacer
import build.wallet.ui.model.StandardClick
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
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemTreatment
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
  trackerScreenId: EventTrackerScreenId,
  emptyState: Boolean,
  modelName: String,
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
  onResetDevice: (() -> Unit)?,
  onBack: () -> Unit,
  multipleFingerprintsEnabled: Boolean,
  resetDeviceEnabled: Boolean,
  onManageFingerprints: () -> Unit,
) = FormBodyModel(
  id = trackerScreenId,
  onBack = onBack,
  toolbar =
    ToolbarModel(
      leadingAccessory = BackAccessory(onClick = onBack),
      middleAccessory = ToolbarMiddleAccessoryModel(title = "Bitkey Device")
    ),
  header = null,
  mainContentList =
    immutableListOfNotNull(
      DataList(
        hero =
          DataHero(
            image =
              IconModel(
                iconImage =
                  LocalImage(
                    icon = BitkeyDevice3D
                  ),
                iconSize = XLarge,
                iconOpacity = 0.3f.takeIf { emptyState }
              ),
            title =
              (
                if (replacementPending != null) {
                  "Replacement pending..."
                } else if (updateVersion != null) {
                  "Update available"
                } else {
                  "Up to date"
                }
              ).takeUnless { emptyState },
            subtitle = (replacementPending ?: currentVersion).takeUnless { emptyState },
            button =
              replacementPending?.let {
                ButtonModel(
                  text = "Manage",
                  treatment = Secondary,
                  size = Footer,
                  onClick = StandardClick { onManageReplacement?.invoke() }
                )
              } ?: updateVersion?.let {
                ButtonModel(
                  text = "Update to $updateVersion",
                  treatment = Primary,
                  size = Footer,
                  onClick = StandardClick { onUpdateVersion?.invoke() }
                )
              }
          ),
        items = run {
          val sideTextType = DataList.Data.SideTextType.REGULAR.takeIf { emptyState }
            ?: DataList.Data.SideTextType.MEDIUM
          val sideTextTreatment = DataList.Data.SideTextTreatment.SECONDARY.takeIf { emptyState }
            ?: DataList.Data.SideTextTreatment.PRIMARY
          immutableListOf(
            DataList.Data(
              title = "Model name",
              sideText = modelName,
              sideTextType = sideTextType,
              sideTextTreatment = sideTextTreatment,
              showBottomDivider = true
            ),
            DataList.Data(
              title = "Model number",
              sideText = modelNumber,
              sideTextType = sideTextType,
              sideTextTreatment = sideTextTreatment,
              showBottomDivider = true
            ),
            DataList.Data(
              title = "Serial number",
              sideText = serialNumber,
              sideTextType = sideTextType,
              sideTextTreatment = sideTextTreatment,
              showBottomDivider = true
            ),
            DataList.Data(
              title = "Firmware version",
              sideText = currentVersion,
              sideTextType = sideTextType,
              sideTextTreatment = sideTextTreatment,
              showBottomDivider = true
            ),
            DataList.Data(
              title = "Last known charge",
              sideText = deviceCharge,
              sideTextType = sideTextType,
              sideTextTreatment = sideTextTreatment,
              showBottomDivider = true
            ),
            DataList.Data(
              title = "Last sync",
              sideText = lastSyncDate,
              sideTextType = sideTextType,
              sideTextTreatment = sideTextTreatment
            )
          )
        },
        buttons =
          immutableListOf(
            ButtonModel(
              text = "Sync device info",
              treatment = TertiaryPrimaryNoUnderline,
              leadingIcon = SmallIconSync,
              size = Compact,
              onClick = StandardClick { onSyncDeviceInfo() }
            )
          )
      ),
      ListGroup(
        listGroupModel = ListGroupModel(
          items = immutableListOfNotNull(
            ListItemModel(
              title = "Fingerprints",
              treatment = ListItemTreatment.SECONDARY,
              trailingAccessory = ListItemAccessory.drillIcon(tint = IconTint.On30),
              onClick = onManageFingerprints
            ).takeIf { multipleFingerprintsEnabled },
            ListItemModel(
              title = "Reset device",
              treatment = ListItemTreatment.SECONDARY,
              trailingAccessory = ListItemAccessory.drillIcon(tint = IconTint.On30),
              onClick = onResetDevice
            ).takeIf { resetDeviceEnabled }
          ),
          style = ListGroupStyle.CARD_GROUP_DIVIDER
        )
      ).takeIf { multipleFingerprintsEnabled || resetDeviceEnabled },
      if (replacementPending == null) {
        Button(
          item =
            ButtonModel(
              text = "Replace device",
              treatment = TertiaryDestructive,
              size = Footer,
              onClick = StandardClick { onReplaceDevice() },
              isEnabled = replaceDeviceEnabled
            )
        )
      } else {
        Spacer()
      }
    ),
  primaryButton = null
)
