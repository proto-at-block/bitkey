package build.wallet.f8e.socrec.models

import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.ktor.result.RedactedResponseBody
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CreateTrustedContactInvitationResponseBody(
  val invitation: CreateTrustedContactInvitation,
) : RedactedResponseBody

@Serializable
internal data class CreateTrustedContactInvitation(
  @SerialName("recovery_relationship_id")
  val recoveryRelationshipId: String,
  @SerialName("trusted_contact_alias")
  val trustedContactAlias: TrustedContactAlias,
  @SerialName("code")
  val code: String,
  @SerialName("code_bit_length")
  val codeBitLength: Int,
  @SerialName("expires_at")
  val expiresAt: Instant,
)
