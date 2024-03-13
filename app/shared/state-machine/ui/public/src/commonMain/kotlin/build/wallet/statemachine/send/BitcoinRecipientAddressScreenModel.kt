package build.wallet.statemachine.send

import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.SmallIconScan
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.AddressInput
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Secondary
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.input.TextFieldModel.KeyboardType.Default
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.toImmutableList

/**
 * A function which creates the form screen model for the recipient screen
 *
 * @param enteredText - The current entered address text
 * @param onEnteredTextChanged - Change handler for the input field
 * @param continueButtonEnabled - Flag for conditionally enabling continue primary button
 * @param showPasteButton - Flag for showing paste button within the input field
 * @param onContinueClick - Click handler for primary button
 * @param onBack - Handler for back press
 * @param onScanQrCodeClick - Click handler for invoking the qr code scanning screen
 * @param onPasteButtonClick - click handler for paste button within the input field
 */
fun BitcoinRecipientAddressScreenModel(
  enteredText: String,
  warningText: String?,
  onEnteredTextChanged: (String) -> Unit,
  showPasteButton: Boolean,
  onContinueClick: (() -> Unit)?,
  onBack: () -> Unit,
  onScanQrCodeClick: () -> Unit,
  onPasteButtonClick: () -> Unit,
) = FormBodyModel(
  onBack = onBack,
  toolbar =
    ToolbarModel(
      leadingAccessory = CloseAccessory(onClick = onBack),
      middleAccessory = ToolbarMiddleAccessoryModel(title = "Recipient"),
      trailingAccessory =
        IconAccessory(
          model =
            IconButtonModel(
              iconModel =
                IconModel(
                  icon = SmallIconScan,
                  iconSize = IconSize.Accessory,
                  iconBackgroundType = IconBackgroundType.Circle(circleSize = IconSize.Regular)
                ),
              onClick = StandardClick(onScanQrCodeClick)
            )
        )
    ),
  header = null,
  mainContentList =
    listOfNotNull(
      AddressInput(
        fieldModel =
          TextFieldModel(
            value = enteredText,
            placeholderText = "Bitcoin Address",
            onValueChange = { newText, _ -> onEnteredTextChanged(newText) },
            keyboardType = Default
          ),
        trailingButtonModel =
          if (showPasteButton) {
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
      ),
      warningText?.let {
        // TODO (W-4075): Make this tinted orange
        FormMainContentModel.Explainer(
          items =
            immutableListOf(
              FormMainContentModel.Explainer.Statement(
                leadingIcon = Icon.SmallIconWarning,
                title = null,
                body = warningText,
                treatment = FormMainContentModel.Explainer.Statement.Treatment.WARNING
              )
            )
        )
      }
    ).toImmutableList(),
  primaryButton =
    ButtonModel(
      text = "Continue",
      size = Footer,
      onClick = StandardClick { onContinueClick?.invoke() },
      isEnabled = onContinueClick != null
    ),
  secondaryButton = null,
  id = SendEventTrackerScreenId.SEND_ADDRESS_ENTRY,
  eventTrackerShouldTrack = false
)
