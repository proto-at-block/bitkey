package build.wallet.statemachine.recovery.emergencyaccesskit

import build.wallet.analytics.events.screen.id.EmergencyAccessKitTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

data class EmergencyAccessKitCodeNotRecognizedBodyModel(
  val arrivedFromManualEntry: Boolean,
  override val onBack: () -> Unit,
  val onScanQRCode: () -> Unit,
  val onImport: () -> Unit,
) : FormBodyModel(
    id = EmergencyAccessKitTrackerScreenId.CODE_NOT_RECOGNIZED,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory(
        onClick = onBack
      )
    ),
    header = FormHeaderModel(
      icon = Icon.LargeIconWarningFilled,
      headline = "Mobile Key backup code not recognized",
      subline = "Try entering the code again or scanning the QR code."
    ),
    mainContentList = immutableListOf(),
    primaryButton = ButtonModel(
      text = "Scan QR code",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onScanQRCode)
    ),
    secondaryButton = ButtonModel(
      text = if (arrivedFromManualEntry) "Try again" else "Import",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onImport)
    )
  )
