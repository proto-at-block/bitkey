package build.wallet.f8e.socrec.models

import build.wallet.bitkey.socrec.TrustedContactAlias
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CreateTrustedContactInvitationResponseBody(
  val invitation: CreateTrustedContactInvitation,
)

@Serializable
internal data class CreateTrustedContactInvitation(
  @SerialName("recovery_relationship_id")
  val recoveryRelationshipId: String,
  @SerialName("trusted_contact_alias")
  val trustedContactAlias: TrustedContactAlias,
  @SerialName("code")
  val token: String,
  @SerialName("expires_at")
  val expiresAt: Instant,
)
