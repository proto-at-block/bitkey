package build.wallet.bitkey.relationships

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ProtectedCustomerRelationshipStatus {
  @SerialName("ENDORSED")
  ENDORSED,

  @SerialName("UNENDORSED")
  UNENDORSED,
}
