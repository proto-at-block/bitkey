package build.wallet.bitkey.relationships

import bitkey.serialization.DelegateSerializer
import bitkey.serialization.json.JsonEncodingError
import bitkey.serialization.json.encodeToStringResult
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.crypto.KeyPurpose
import build.wallet.crypto.PublicKey
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.util.*
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
  val delegatedDecryptionKey: PublicKey<DelegatedDecryptionKey>,
  @SerialName("hw_endorsement_key")
  val hwAuthPublicKey: HwAuthPublicKey,
  @SerialName("app_endorsement_key")
  @Serializable(with = NestedPubKeySerializer::class)
  val appGlobalAuthPublicKey: PublicKey<AppGlobalAuthKey>,
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

  /**
   * We unintentionally are serializing the auth public key with an extra level of nesting as
   * { "pubKey": "..." }. The [NestedPubKeySerializer] is preserves this behavior since we don't
   * want to break backwards compatiblity. We shouldn't use this serializer anywhere else.
   */
  @Serializable
  data class NestedPublicKey(val pubKey: String)

  private class NestedPubKeySerializer :
    DelegateSerializer<NestedPublicKey, PublicKey<out KeyPurpose>>(NestedPublicKey.serializer()) {
    override fun serialize(data: PublicKey<out KeyPurpose>): NestedPublicKey =
      NestedPublicKey(data.value)

    override fun deserialize(data: NestedPublicKey): PublicKey<out KeyPurpose> =
      PublicKey(data.pubKey)
  }
}
