package build.wallet.f8e.socrec

import build.wallet.bitkey.socrec.EndorsedTrustedContact
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.bitkey.socrec.TrustedContactKeyCertificate
import build.wallet.bitkey.socrec.TrustedContactKeyCertificateAsBase64JsonSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The [F8eEndorsedTrustedContact] type represents an endorsed trusted contact from the perspective
 * of the server (f8e). This type is similar to the [EndorsedTrustedContact] type, which represents
 * an endorsed trusted contact as well but from the perspective of the app. However,
 * unlike [EndorsedTrustedContact], the [F8eEndorsedTrustedContact] does not include an authentication
 * state. This is because the f8e service cannot make assumptions or verify the authentication state
 * of the certificate.
 */
@Serializable
internal data class F8eEndorsedTrustedContact(
  @SerialName("recovery_relationship_id")
  val recoveryRelationshipId: String,
  @SerialName("trusted_contact_alias")
  val trustedContactAlias: TrustedContactAlias,
  @SerialName("delegated_decryption_pubkey_certificate")
  @Serializable(with = TrustedContactKeyCertificateAsBase64JsonSerializer::class)
  val keyCertificate: TrustedContactKeyCertificate,
)
