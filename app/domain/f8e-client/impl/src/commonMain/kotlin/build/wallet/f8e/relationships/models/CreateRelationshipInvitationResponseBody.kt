package build.wallet.f8e.relationships.models

import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.ktor.result.RedactedResponseBody
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CreateRelationshipInvitationResponseBody(
  val invitation: RelationshipInvitation,
) : RedactedResponseBody

@Serializable
internal data class RelationshipInvitation(
  @SerialName("recovery_relationship_id")
  val relationshipId: String,
  @SerialName("trusted_contact_alias")
  val trustedContactAlias: TrustedContactAlias,
  @SerialName("code")
  val code: String,
  @SerialName("code_bit_length")
  val codeBitLength: Int,
  @SerialName("expires_at")
  val expiresAt: Instant,
  @SerialName("trusted_contact_roles")
  val roles: Set<TrustedContactRole>,
)
