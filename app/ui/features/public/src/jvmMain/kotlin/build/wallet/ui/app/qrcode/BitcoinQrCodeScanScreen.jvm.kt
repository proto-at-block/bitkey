package build.wallet.ui.app.qrcode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.send.QrCodeScanBodyModel

/**
 * Desktop (JVM) dev-mode affordance for the QR/camera scanner.
 *
 * Desktop hosts have no camera, so the real scanner cannot run. Instead of dead-ending the flow
 * with a no-op, this renders a simple input that lets a developer paste an address/invoice — or
 * pick one of a few preset test values — and feeds it to the same callback the real scanner
 * invokes ([QrCodeScanBodyModel.onQrCodeScanned]). This keeps the JVM `actual` signature identical
 * to the `expect` while letting hardware-free flows be driven to completion on desktop.
 *
 * This is intentionally only present in the jvmMain source set; Android/iOS use real cameras.
 */
@Composable
internal actual fun NativeQrCodeScanner(model: QrCodeScanBodyModel) {
  var input by remember { mutableStateOf("") }
  val colorScheme = MaterialTheme.colorScheme
  val typography = MaterialTheme.typography

  Column(
    modifier = Modifier
      .fillMaxSize()
      .systemBarsPadding()
      .padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    BasicText(
      text = "Desktop dev QR scanner",
      style = typography.titleMedium.copy(color = colorScheme.onBackground)
    )
    BasicText(
      text = "No camera on desktop. Paste an address or invoice, then submit. " +
        "Or pick a preset below.",
      style = typography.bodyMedium.copy(color = colorScheme.onBackground)
    )

    OutlinedTextField(
      modifier = Modifier.fillMaxWidth(),
      value = input,
      onValueChange = { input = it },
      label = {
        BasicText(
          text = "Address / invoice",
          style = typography.bodySmall.copy(color = colorScheme.onSurfaceVariant)
        )
      },
      singleLine = false
    )

    Button(
      modifier = Modifier.fillMaxWidth(),
      enabled = input.isNotBlank(),
      shape = RoundedCornerShape(12.dp),
      onClick = { model.onQrCodeScanned(input.trim()) }
    ) {
      BasicText(
        text = "Submit",
        style = typography.labelLarge.copy(color = colorScheme.onPrimary)
      )
    }

    BasicText(
      text = "Presets",
      style = typography.titleSmall.copy(color = colorScheme.onBackground)
    )
    PresetQrValues.forEach { (label, value) ->
      OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        onClick = { model.onQrCodeScanned(value) }
      ) {
        BasicText(
          text = label,
          style = typography.labelLarge.copy(color = colorScheme.primary)
        )
      }
    }
  }
}

/**
 * A small set of valid-format Bitcoin test values for driving desktop flows without a camera.
 * These are well-known test/example values only — never real funds.
 */
private val PresetQrValues: List<Pair<String, String>> = listOf(
  "Regtest address" to "bcrt1qrt37mr0kf2th5dgsqq6k87tl8k220e7nj4ts5u",
  "BIP21 invoice" to
    "bitcoin:bcrt1qrt37mr0kf2th5dgsqq6k87tl8k220e7nj4ts5u?amount=0.001",
  "Signet address" to "tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx",
  "Mainnet address" to "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
  "Testnet address" to "tb1q6rz28mcfaxtmd6v789l9rrlrusdprr9pqcpvkl"
)
