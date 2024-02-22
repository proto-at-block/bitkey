package build.wallet.bitkey.socrec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A wallet holder whose wallet is protected by one or more [TrustedContact]s on the other side of the recovery relationship.
 */
@Serializable
data class ProtectedCustomer(
  @SerialName("recovery_relationship_id")
  val recoveryRelationshipId: String,
  @SerialName("customer_alias")
  val alias: ProtectedCustomerAlias,
)
