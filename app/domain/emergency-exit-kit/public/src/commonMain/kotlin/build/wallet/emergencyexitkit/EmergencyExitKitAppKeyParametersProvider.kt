package build.wallet.emergencyexitkit

import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.csek.SealedCsek
import com.github.michaelbull.result.Result

interface EmergencyExitKitAppKeyParametersProvider {
  suspend fun parameters(
    keybox: Keybox,
    sealedCsek: SealedCsek,
  ): Result<AppKeyParameters, Error>
}

data class AppKeyParameters(
  /**
   * The App Key characters material, displayed in Step 4.
   */
  val appKeyCharacters: String,
  /**
   * The QR code text used to generate the QR code of the App Key, displayed in Step 4.
   */
  val appKeyQRCodeText: String,
)
