package build.wallet.f8e.relationships.models

import build.wallet.bitkey.relationships.EncodedTrustedContactKeyCertificate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class EndorsementBody(
  @SerialName("recovery_relationship_id")
  val relationshipId: String,
  @SerialName("delegated_decryption_pubkey_certificate")
  val delegatedDecryptionKeyCertificate: EncodedTrustedContactKeyCertificate,
)
