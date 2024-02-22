package build.wallet.bitkey.socrec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A person that a [ProtectedCustomer] knows and trusts, who they choose to serve as a verification mechanism
 * for Social Recovery.
 */
@Serializable
data class TrustedContact(
  @SerialName("recovery_relationship_id")
  override val recoveryRelationshipId: String,
  @SerialName("trusted_contact_alias")
  override val trustedContactAlias: TrustedContactAlias,
  @SerialName("trusted_contact_identity_pubkey")
  val identityKey: TrustedContactIdentityKey,
) : RecoveryContact
