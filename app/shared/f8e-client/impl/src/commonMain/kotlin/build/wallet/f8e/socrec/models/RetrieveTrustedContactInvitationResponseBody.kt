package build.wallet.f8e.socrec.models

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RetrieveTrustedContactInvitationResponseBody(
  val invitation: RetrieveTrustedContactInvitation,
)

@Serializable
internal data class RetrieveTrustedContactInvitation(
  @SerialName("expires_at")
  val expiresAt: Instant,
  @SerialName("recovery_relationship_id")
  val recoveryRelationshipId: String,
)
