package build.wallet.bitkey.relationships

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A wallet holder whose wallet is protected by one or more [EndorsedTrustedContact]s on the other side of the recovery relationship.
 */
@Serializable
data class ProtectedCustomer(
  @SerialName("recovery_relationship_id")
  override val relationshipId: String,
  @SerialName("customer_alias")
  val alias: ProtectedCustomerAlias,
  @SerialName("trusted_contact_roles")
  val roles: Set<TrustedContactRole>,
) : RecoveryEntity

/**
 * We sync all relationships, including inheritance benefactors which become [ProtectedCustomer]s.
 * This filters that to only be the [ProtectedCustomer]s that are relevant for Social Recovery
 * purposes.
 */
fun List<ProtectedCustomer>.socialRecoveryProtectedCustomers(): List<ProtectedCustomer> {
  return this.filter { it.roles.contains(TrustedContactRole.SocialRecoveryContact) }
}
