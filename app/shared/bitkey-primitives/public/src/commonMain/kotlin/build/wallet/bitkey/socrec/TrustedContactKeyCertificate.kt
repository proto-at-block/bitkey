package build.wallet.bitkey.socrec

import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.logging.logFailure
import build.wallet.serialization.json.JsonEncodingError
import build.wallet.serialization.json.encodeToStringResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.util.encodeBase64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A certificate that endorses a trusted contact (TC) key's authenticity.
 *
 * Each endorsed and verified TC has its own key certificate.
 *
 * This certificate can be verified as long as the app has either the original
 * hardware auth key or the original app global auth key:
 *    - if the PC has not gone through D&N recovery, either the app or hardware auth key can be used to
 *      verify the cert.
 *    - if the PC did a Lost Hardware recovery, we can use the app's auth key to verify the TC identity key.
 *      The certs will be rotated post recovery.
 *    - if the PC did a Lost App recovery, we can use the hardware to verify the old app auth key, and
 *      then use that key to verify the TC identity key. The certs will be rotated post recovery.
 */
@Serializable
data class TrustedContactKeyCertificate(
  @SerialName("delegated_decryption_key")
  val delegatedDecryptionKey: DelegatedDecryptionKey,
  @SerialName("hw_endorsement_key")
  val hwAuthPublicKey: HwAuthPublicKey,
  @SerialName("app_endorsement_key")
  val appGlobalAuthPublicKey: AppGlobalAuthPublicKey,
  @SerialName("hw_signature")
  val appAuthGlobalKeyHwSignature: AppGlobalAuthKeyHwSignature,
  @SerialName("app_signature")
  val trustedContactIdentityKeyAppSignature: TcIdentityKeyAppSignature,
) {
  /**
   * Encodes this [TrustedContactKeyCertificate] to a base64 string of JSON representation,
   * returns [EncodedTrustedContactKeyCertificate].
   */
  fun encode(): Result<EncodedTrustedContactKeyCertificate, JsonEncodingError> {
    return Json
      .encodeToStringResult(this)
      .map { json ->
        EncodedTrustedContactKeyCertificate(base64 = json.encodeBase64())
      }
      .logFailure { "Error encoding certificate $this" }
  }
}
