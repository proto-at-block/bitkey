package build.wallet.statemachine.settings.full.device

import bitkey.account.HardwareType
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormDesignSystemV2Model
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.*
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel.Treatment.*
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.list.*
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
data class DeviceSettingsFormBodyModel(
  val trackerScreenId: EventTrackerScreenId,
  val emptyState: Boolean,
  val showRealtimeMedia: Boolean = true,
  val modelName: String,
  val currentVersion: String,
  val updateVersion: String?,
  val modelNumber: String,
  val serialNumber: String,
  val deviceCharge: String,
  val deviceBatteryPercentage: Int? = null,
  val lastSyncDate: String,
  val hardwareType: HardwareType = HardwareType.W1,
  val replacementPending: String?,
  val replaceDeviceEnabled: Boolean,
  val showFingerprintsRow: Boolean = hardwareType != HardwareType.W3,
  val onUpdateVersion: (() -> Unit)?,
  val onSyncDeviceInfo: () -> Unit,
  val onReplaceDevice: () -> Unit,
  val onShowAboutSheet: () -> Unit = {},
  val onManageReplacement: (() -> Unit)?,
  val onPairDevice: (() -> Unit)?,
  val onWipeDevice: (() -> Unit)?,
  val onUpgradeDevice: (() -> Unit)? = null,
  override val onBack: () -> Unit,
  val onManageFingerprints: () -> Unit,
) : FormBodyModel(
    id = trackerScreenId,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = BackAccessory(onClick = onBack),
      middleAccessory = ToolbarMiddleAccessoryModel(title = "Bitkey Device")
    ),
    designSystemV2Model = FormDesignSystemV2Model(
      title = "Bitkey Device",
      toolbar = ToolbarModel(
        leadingAccessory = BackAccessory(onClick = onBack)
      )
    ),
    header = null,
    mainContentList = immutableListOfNotNull(
      DeviceStatusCard(
        deviceVideo = if (!emptyState && showRealtimeMedia) DeviceStatusCard.VideoContent.BITKEY_ROTATE else null,
        deviceSerialNumber =
          serialNumber.takeIf {
            !emptyState && hardwareType == HardwareType.W3
          },
        deviceBatteryPercentage =
          deviceBatteryPercentage?.takeIf {
            !emptyState && hardwareType == HardwareType.W3
          },
        hardwareType = hardwareType,
        deviceImage = if (emptyState || !showRealtimeMedia) {
          IconModel(
            iconImage = LocalImage(icon = Icon.BitkeyFrontLit),
            iconSize = IconSize.XXXLarge,
            iconOpacity = if (emptyState) 0.3f else 0.0f
          )
        } else {
          null
        },
        statusCallout = CalloutModel(
          title = if (emptyState) {
            "No Bitkey found"
          } else if (replacementPending != null) {
            "Replacement pending..."
          } else if (updateVersion != null) {
            "Update available"
          } else {
            "Last synced"
          },
          subtitle = StringModel(
            if (emptyState) {
              "Add a bitkey device"
            } else if (replacementPending != null) {
              replacementPending
            } else if (updateVersion != null) {
              updateVersion
            } else {
              lastSyncDate
            }
          ),
          treatment = when {
            emptyState -> CalloutModel.Treatment.White
            replacementPending != null -> CalloutModel.Treatment.White
            updateVersion != null -> CalloutModel.Treatment.White
            else -> CalloutModel.Treatment.White
          },
          trailingIcon = when {
            emptyState -> Icon.SmallIconArrowRight
            replacementPending != null -> Icon.SmallIconArrowRight
            updateVersion != null -> Icon.SmallIconArrowRight
            else -> Icon.SmallIconRefresh
          },
          onClick = when {
            emptyState -> StandardClick { onPairDevice?.invoke() }
            replacementPending != null -> StandardClick { onManageReplacement?.invoke() }
            updateVersion != null -> StandardClick { onUpdateVersion?.invoke() }
            else -> StandardClick(onSyncDeviceInfo)
          }
        )
      ),
      SettingsList(
        header = "Device options",
        items = immutableListOfNotNull(
          SettingsList.SettingsListItem(
            title = "About",
            icon = Icon.SmallIconInformation,
            isEnabled = !emptyState,
            onClick = {
              onShowAboutSheet()
            }
          ),
          if (showFingerprintsRow) {
            SettingsList.SettingsListItem(
              title = "Fingerprints",
              icon = Icon.SmallIconFingerprint,
              isEnabled = !emptyState,
              onClick = onManageFingerprints
            )
          } else {
            null
          },
          SettingsList.SettingsListItem(
            title = "Wipe device",
            icon = Icon.SmallIconBitkeyReset,
            isEnabled = !emptyState,
            onClick = onWipeDevice
          ),
          if (onUpgradeDevice != null && replacementPending == null) {
            SettingsList.SettingsListItem(
              title = "Upgrade device",
              icon = Icon.SmallIconBitkey,
              isEnabled = !emptyState,
              onClick = onUpgradeDevice
            )
          } else {
            null
          },
          if (replacementPending == null) {
            SettingsList.SettingsListItem(
              title = "Replace device",
              icon = Icon.SmallIconBitkey,
              treatment = ListItemTreatment.DESTRUCTIVE,
              isEnabled = replaceDeviceEnabled,
              onClick = onReplaceDevice
            )
          } else {
            null
          }
        )
      )
    ),
    primaryButton = null
  )
