package build.wallet.emergencyaccesskit

import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.csek.SealedCsek
import com.github.michaelbull.result.Result

interface EmergencyAccessKitMobileKeyParametersProvider {
  suspend fun parameters(
    keybox: Keybox,
    sealedCsek: SealedCsek,
  ): Result<MobileKeyParameters, Error>
}

data class MobileKeyParameters(
  /**
   * The App Key characters material, displayed in Step 4.
   */
  val mobileKeyCharacters: String,
  /**
   * The QR code text used to generate the QR code of the App Key, displayed in Step 4.
   */
  val mobileKeyQRCodeText: String,
)
