package build.wallet.statemachine.send.signtransaction

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import bitkey.account.HardwareType
import build.wallet.Progress
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.app.nfc.SignTransactionNfcScreen

/**
 * UI body model for transaction signing NFC flow.
 *
 * Displays different states during the transaction signing process:
 * - Searching for device
 * - Transferring PSBT data (W3 only, with progress)
 * - Waiting for user confirmation on device
 * - Lost connection (with ability to retry)
 * - Success
 *
 * @property hardwareType The type of hardware device being tapped. Used to display
 * the appropriate device imagery (W1 vs W3) during the NFC session.
 */
data class SignTransactionNfcBodyModel(
  val onCancel: (() -> Unit)?,
  val status: Status,
  val hardwareType: HardwareType = HardwareType.W3,
  val showNativeSheetOnIos: Boolean = false,
  val onHelpClick: (() -> Unit)? = null,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo?,
) : BodyModel() {
  @Composable
  override fun render(modifier: Modifier) {
    SignTransactionNfcScreen(
      modifier = modifier,
      model = this
    )
  }

  sealed interface Status {
    /**
     * Searching for NFC device to begin transaction signing.
     */
    data object Searching : Status

    /**
     * Connected and signing transaction on device.
     * Shown with an indeterminate progress indicator for W1 signing
     * where no streaming progress is available.
     */
    data object Signing : Status

    /**
     * Transferring PSBT data to the device (W3 chunked transfer).
     *
     * @property progress Transfer progress
     */
    data class Transferring(val progress: Progress) : Status

    /**
     * NFC connection lost during transfer or confirmation.
     * User can retry by tapping again.
     *
     * @property progress Progress achieved before connection was lost
     */
    data class LostConnection(val progress: Progress) : Status

    /**
     * Transaction successfully signed by hardware device.
     */
    data object Success : Status
  }
}
