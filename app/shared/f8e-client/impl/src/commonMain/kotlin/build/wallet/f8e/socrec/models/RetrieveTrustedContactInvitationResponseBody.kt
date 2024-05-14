package build.wallet.f8e.socrec.models

import build.wallet.bitkey.socrec.ProtectedCustomerEnrollmentPakeKey
import build.wallet.crypto.PublicKey
import build.wallet.ktor.result.RedactedResponseBody
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RetrieveTrustedContactInvitationResponseBody(
  val invitation: RetrieveTrustedContactInvitation,
) : RedactedResponseBody

@Serializable
internal data class RetrieveTrustedContactInvitation(
  @SerialName("expires_at")
  val expiresAt: Instant,
  @SerialName("recovery_relationship_id")
  val recoveryRelationshipId: String,
  @SerialName("protected_customer_enrollment_pake_pubkey")
  val protectedCustomerEnrollmentPakePubkey: PublicKey<ProtectedCustomerEnrollmentPakeKey>,
)
