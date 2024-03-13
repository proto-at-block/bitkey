package build.wallet.f8e.socrec.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class VerifyChallengeRequestBody(
  @SerialName("recovery_relationship_id")
  val recoveryRelationshipId: String,
  @SerialName("counter")
  val code: Int,
)
