package build.wallet.f8e.socrec.models

import build.wallet.bitkey.socrec.EncodedTrustedContactKeyCertificate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class EndorsementBody(
  @SerialName("recovery_relationship_id")
  val recoveryRelationshipId: String,
  @SerialName("delegated_decryption_pubkey_certificate")
  val delegatedDecryptionKeyCertificate: EncodedTrustedContactKeyCertificate,
)
