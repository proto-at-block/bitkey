package build.wallet.statemachine.send

import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.Icon

/**
 * Model used for showing the QR Scan overlay
 *
 * @property onQrCodeScanned - Method invoked when the scanner has successfully parsed a QR code
 * string
 * @property onEnterAddressClick - Method invoked when the user clicks enter address to manually
 * enter an address
 * @property onClose - Method invoked when the user attempts to close the QR scan view
 */
fun BitcoinQrCodeScanBodyModel(
  showSendToCopiedAddressButton: Boolean,
  onQrCodeScanned: (String) -> Unit,
  onEnterAddressClick: () -> Unit,
  onClose: () -> Unit,
  onSendToCopiedAddressClick: () -> Unit,
) = QrCodeScanBodyModel(
  headline = "Recipient",
  reticleLabel = "Scan a Bitcoin address",
  onQrCodeScanned = onQrCodeScanned,
  onClose = onClose,
  primaryButtonData =
    ButtonDataModel(
      text = "Enter address",
      onClick = onEnterAddressClick
    ),
  secondaryButtonData =
    ButtonDataModel(
      text = "Send to copied address",
      onClick = onSendToCopiedAddressClick,
      leadingIcon = Icon.SmallIconClipboard
    ).takeIf { showSendToCopiedAddressButton },
  // We don't want to track this for privacy reasons
  eventTrackerScreenInfo = null
)
