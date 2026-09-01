package build.wallet.bitkey.relationships

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A wallet holder whose wallet this account protects or will protect once setup is complete.
 */
@Serializable
data class ProtectedCustomer(
  @SerialName("recovery_relationship_id")
  override val id: RelationshipId,
  @SerialName("customer_alias")
  val alias: ProtectedCustomerAlias,
  @SerialName("trusted_contact_roles")
  val roles: Set<TrustedContactRole>,
  @SerialName("relationship_status")
  val relationshipStatus: ProtectedCustomerRelationshipStatus = ProtectedCustomerRelationshipStatus.ENDORSED,
) : RecoveryEntity

/**
 * We sync all relationships, including inheritance benefactors which become [ProtectedCustomer]s.
 * This filters that to only be the [ProtectedCustomer]s that are relevant for Social Recovery
 * purposes.
 */
fun List<ProtectedCustomer>.socialRecoveryProtectedCustomers(): List<ProtectedCustomer> {
  return this.filter { it.roles.contains(TrustedContactRole.SocialRecoveryContact) }
}
