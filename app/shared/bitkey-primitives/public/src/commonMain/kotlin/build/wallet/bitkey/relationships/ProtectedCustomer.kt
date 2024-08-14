package build.wallet.bitkey.relationships

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A wallet holder whose wallet is protected by one or more [EndorsedTrustedContact]s on the other side of the recovery relationship.
 */
@Serializable
data class ProtectedCustomer(
  @SerialName("recovery_relationship_id")
  val relationshipId: String,
  @SerialName("customer_alias")
  val alias: ProtectedCustomerAlias,
  @SerialName("trusted_contact_roles")
  val roles: Set<TrustedContactRole>,
)
