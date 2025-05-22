package build.wallet.f8e.relationships

import build.wallet.bitkey.relationships.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The [F8eEndorsedTrustedContact] type represents an endorsed Recovery Contact from the perspective
 * of the server (f8e). This type is similar to the [EndorsedTrustedContact] type, which represents
 * an endorsed Recovery Contact as well but from the perspective of the app. However,
 * unlike [EndorsedTrustedContact], the [F8eEndorsedTrustedContact] does not include an authentication
 * state. This is because the f8e service cannot make assumptions or verify the authentication state
 * of the certificate.
 */
@Serializable
internal data class F8eEndorsedTrustedContact(
  @SerialName("recovery_relationship_id")
  val relationshipId: String,
  @SerialName("trusted_contact_alias")
  val trustedContactAlias: TrustedContactAlias,
  @SerialName("delegated_decryption_pubkey_certificate")
  @Serializable(with = TrustedContactKeyCertificateAsBase64JsonSerializer::class)
  val keyCertificate: TrustedContactKeyCertificate,
  @SerialName("trusted_contact_roles")
  val roles: Set<TrustedContactRole>,
)
