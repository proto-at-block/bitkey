package build.wallet.statemachine.settings.full.device

import androidx.compose.runtime.Composable
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.Sheet
import bitkey.ui.framework.SheetPresenter
import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemTreatment

data class AboutDeviceSheet(
  val modelName: String,
  val modelNumber: String,
  val serialNumber: String,
  val currentVersion: String,
  val deviceCharge: String,
  val lastSyncDate: String,
  val emptyState: Boolean,
  val onDismiss: () -> Unit,
  val onSyncDeviceInfo: () -> Unit,
  override val origin: Screen,
) : Sheet

@BitkeyInject(ActivityScope::class)
class AboutDeviceSheetPresenter : SheetPresenter<AboutDeviceSheet> {
  @Composable
  override fun model(
    navigator: Navigator,
    sheet: AboutDeviceSheet,
  ): SheetModel {
    return AboutDeviceSheetModel(
      modelName = sheet.modelName,
      modelNumber = sheet.modelNumber,
      serialNumber = sheet.serialNumber,
      currentVersion = sheet.currentVersion,
      deviceCharge = sheet.deviceCharge,
      lastSyncDate = sheet.lastSyncDate,
      emptyState = sheet.emptyState,
      onDismiss = sheet.onDismiss,
      onSyncDeviceInfo = sheet.onSyncDeviceInfo
    )
  }
}

fun AboutDeviceSheetModel(
  modelName: String,
  modelNumber: String,
  serialNumber: String,
  currentVersion: String,
  deviceCharge: String,
  lastSyncDate: String,
  emptyState: Boolean,
  onDismiss: () -> Unit,
  onSyncDeviceInfo: () -> Unit,
) = AboutDeviceSheetBodyModel(
  modelName = modelName,
  modelNumber = modelNumber,
  serialNumber = serialNumber,
  currentVersion = currentVersion,
  deviceCharge = deviceCharge,
  lastSyncDate = lastSyncDate,
  emptyState = emptyState,
  onDismiss = onDismiss,
  onSyncDeviceInfo = onSyncDeviceInfo
).asSheetModalScreen(onClosed = onDismiss)

data class AboutDeviceSheetBodyModel(
  val modelName: String,
  val modelNumber: String,
  val serialNumber: String,
  val currentVersion: String,
  val deviceCharge: String,
  val lastSyncDate: String,
  val emptyState: Boolean,
  val onDismiss: () -> Unit,
  val onSyncDeviceInfo: () -> Unit,
) : FormBodyModel(
    id = SettingsEventTrackerScreenId.SETTINGS_DEVICE_INFO,
    onBack = onDismiss,
    toolbar = null,
    header = FormHeaderModel(
      headline = "About Device",
      subline = null
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.ListGroup(
        listGroupModel = ListGroupModel(
          items = immutableListOf(
            ListItemModel(
              title = "Model name",
              sideText = modelName,
              treatment = ListItemTreatment.PRIMARY,
              enabled = true
            ),
            ListItemModel(
              title = "Model number",
              sideText = modelNumber,
              enabled = true
            ),
            ListItemModel(
              title = "Serial number",
              sideText = serialNumber,
              enabled = true
            ),
            ListItemModel(
              title = "Firmware version",
              sideText = currentVersion,
              enabled = true
            ),
            ListItemModel(
              title = "Last known charge",
              sideText = deviceCharge,
              enabled = true
            ),
            ListItemModel(
              title = "Last sync",
              sideText = lastSyncDate,
              enabled = true
            )
          ),
          style = ListGroupStyle.DIVIDER
        )
      )
    ),
    primaryButton = null,
    secondaryButton = ButtonModel(
      text = "Sync device info",
      leadingIcon = Icon.SmallIconRefresh,
      onClick = StandardClick(onSyncDeviceInfo),
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer
    ),
    renderContext = RenderContext.Sheet
  )
