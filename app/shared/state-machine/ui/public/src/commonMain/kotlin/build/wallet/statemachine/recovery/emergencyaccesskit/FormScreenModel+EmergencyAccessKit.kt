package build.wallet.statemachine.recovery.emergencyaccesskit

import build.wallet.analytics.events.screen.id.EmergencyAccessKitTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Companion.BitkeyInteractionButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Treatment.Secondary
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

fun EmergencyAccessKitImportWalletModel(
  onBack: () -> Unit,
  onScanQRCode: () -> Unit,
  onEnterManually: () -> Unit,
) = FormBodyModel(
  id = EmergencyAccessKitTrackerScreenId.SELECT_IMPORT_METHOD,
  onBack = onBack,
  toolbar =
    ToolbarModel(
      leadingAccessory =
        ToolbarAccessoryModel.IconAccessory.BackAccessory(
          onClick = onBack
        )
    ),
  header =
    FormHeaderModel(
      headline = "Import your wallet using your Emergency access kit",
      subline = "Your Emergency access kit is a PDF document located in your device's cloud backup."
    ),
  mainContentList =
    immutableListOf(
      FormMainContentModel.Explainer(
        items =
          immutableListOf(
            FormMainContentModel.Explainer.Statement(
              leadingIcon = Icon.SmallIconCloud,
              title = "Find your Emergency Access Kit",
              body = "Navigate to your device's cloud file manager to locate and download your Emergency Access Kit."
            ),
            FormMainContentModel.Explainer.Statement(
              leadingIcon = Icon.SmallIconQrCode,
              title = "Scan or Import manually",
              body = "Scan the QR code for easy access to your Bitkey backup or enter the details manually."
            )
          )
      )
    ),
  primaryButton =
    ButtonModel(
      text = "Enter details manually",
      treatment = Secondary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick { onEnterManually() }
    ),
  secondaryButton =
    ButtonModel(
      text = "Scan QR code",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick { onScanQRCode() }
    )
)

fun EmergencyAccessKitImportPasteMobileKeyModel(
  enteredText: String,
  onBack: () -> Unit,
  onEnterTextChanged: (String) -> Unit,
  onPasteButtonClick: () -> Unit,
  onContinue: () -> Unit,
) = FormBodyModel(
  id = EmergencyAccessKitTrackerScreenId.IMPORT_TEXT_KEY,
  onBack = onBack,
  toolbar =
    ToolbarModel(
      leadingAccessory =
        ToolbarAccessoryModel.IconAccessory.BackAccessory(
          onClick = onBack
        )
    ),
  header =
    FormHeaderModel(
      headline = "Enter your Emergency Access Kit details",
      sublineModel =
        LabelModel.StringWithStyledSubstringModel.from(
          string = "Copy the code from the box labeled Mobile key backup directly from your emergency access kit, and paste it into the field below",
          boldedSubstrings = immutableListOf("Mobile key backup")
        )
    ),
  mainContentList =
    immutableListOf(
      FormMainContentModel.AddressInput(
        fieldModel =
          TextFieldModel(
            value = enteredText,
            placeholderText = "Mobile key code",
            onValueChange = { newText, _ -> onEnterTextChanged(newText) },
            keyboardType = TextFieldModel.KeyboardType.Default
          ),
        trailingButtonModel =
          if (enteredText.isEmpty()) {
            ButtonModel(
              text = "Paste",
              leadingIcon = Icon.SmallIconClipboard,
              treatment = Secondary,
              size = Compact,
              onClick = StandardClick { onPasteButtonClick() }
            )
          } else {
            null
          }
      )
    ),
  primaryButton =
    ButtonModel(
      text = "Continue",
      isEnabled = enteredText.isNotEmpty(),
      size = ButtonModel.Size.Footer,
      onClick = StandardClick { onContinue() }
    )
)

// TODO [BKR-730] Implement QR code scanning
fun EmergencyAccessKitScanQRCodeModel(
  onBack: () -> Unit,
  onScanQRCode: (String) -> Unit,
) = FormBodyModel(
  id = EmergencyAccessKitTrackerScreenId.SCAN_QR_CODE,
  onBack = onBack,
  toolbar =
    ToolbarModel(
      leadingAccessory =
        ToolbarAccessoryModel.IconAccessory.BackAccessory(
          onClick = onBack
        )
    ),
  header =
    FormHeaderModel(
      headline = "Scan QR Code",
      subline = "TODO [BKR-730] implement QR scanning"
    ),
  mainContentList = immutableListOf(),
  primaryButton =
    ButtonModel(
      text = "Fake Valid Data",
      size = ButtonModel.Size.Footer,
      onClick =
        StandardClick {
          onScanQRCode("kQnG62aGRXF4Uh3bVDFkkADBfo9TJKJ4Ff69MYxuAWQqXDgiKQJMt862hu")
        }
    ),
  secondaryButton =
    ButtonModel(
      text = "Fake Invalid Data",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick { onScanQRCode("Invalid Payload!") }
    )
)

fun EmergencyAccessKitCodeNotRecognized(
  arrivedFromManualEntry: Boolean,
  onBack: () -> Unit,
  onScanQRCode: () -> Unit,
  onImport: () -> Unit,
) = FormBodyModel(
  id = EmergencyAccessKitTrackerScreenId.CODE_NOT_RECOGNIZED,
  onBack = onBack,
  toolbar =
    ToolbarModel(
      leadingAccessory =
        ToolbarAccessoryModel.IconAccessory.BackAccessory(
          onClick = onBack
        )
    ),
  header =
    FormHeaderModel(
      icon = Icon.LargeIconWarningFilled,
      headline = "Mobile Key backup code not recognized",
      subline = "Try entering the code again or scanning the QR code."
    ),
  mainContentList = immutableListOf(),
  primaryButton =
    ButtonModel(
      text = "Scan QR code",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick { onScanQRCode() }
    ),
  secondaryButton =
    ButtonModel(
      text =
        if (arrivedFromManualEntry) "Try again" else "Import",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick { onImport() }
    )
)

fun EmergencyAccessKitRestoreWallet(
  onBack: () -> Unit,
  onRestore: () -> Unit,
) = FormBodyModel(
  onBack = onBack,
  toolbar =
    ToolbarModel(
      leadingAccessory =
        ToolbarAccessoryModel.IconAccessory.BackAccessory(
          onClick = onBack
        )
    ),
  header =
    FormHeaderModel(
      headline = "Restore your wallet",
      subline = "Access your wallet on this phone using the Emergency Access backup of your mobile key, with approval from your Bitkey device."
    ),
  primaryButton =
    BitkeyInteractionButtonModel(
      text = "Restore Bitkey Wallet",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onRestore)
    ),
  id = EmergencyAccessKitTrackerScreenId.RESTORE_YOUR_WALLET
)
