package build.wallet.ui.app.send

import androidx.compose.runtime.Composable
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.send.BitcoinRecipientAddressScreenModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.tooling.PreviewWalletTheme
import io.kotest.core.spec.style.FunSpec

class BitcoinRecipientAddressScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("bitcoin recipient address screen - no entry") {
    paparazzi.snapshot {
      BitcoinRecipientAddressWithoutEntryScreenPreview()
    }
  }

  test("bitcoin recipient address screen - no entry, with paste button") {
    paparazzi.snapshot {
      BitcoinRecipientAddressWithoutEntryScreenWithPasteButtonPreview()
    }
  }

  test("bitcoin recipient address screen - with entry") {
    paparazzi.snapshot {
      BitcoinRecipientAddressWithEntryScreenPreview()
    }
  }
})

@Composable
internal fun BitcoinRecipientAddressWithoutEntryScreenPreview() {
  PreviewWalletTheme {
    FormScreen(
      BitcoinRecipientAddressScreenModel(
        enteredText = "",
        warningText = null,
        showPasteButton = false,
        onEnteredTextChanged = {},
        onContinueClick = null,
        onBack = {},
        onScanQrCodeClick = {},
        onPasteButtonClick = {}
      )
    )
  }
}

@Composable
internal fun BitcoinRecipientAddressWithoutEntryScreenWithPasteButtonPreview() {
  PreviewWalletTheme {
    FormScreen(
      BitcoinRecipientAddressScreenModel(
        enteredText = "",
        warningText = null,
        showPasteButton = true,
        onEnteredTextChanged = {},
        onContinueClick = null,
        onBack = {},
        onScanQrCodeClick = {},
        onPasteButtonClick = {}
      )
    )
  }
}

@Composable
internal fun BitcoinRecipientAddressWithEntryScreenPreview() {
  PreviewWalletTheme {
    FormScreen(
      BitcoinRecipientAddressScreenModel(
        enteredText = "0x1234",
        warningText = "Some warning text",
        showPasteButton = false,
        onEnteredTextChanged = {},
        onContinueClick = {},
        onBack = {},
        onScanQrCodeClick = {},
        onPasteButtonClick = {}
      )
    )
  }
}
