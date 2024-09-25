package build.wallet.f8e.relationships

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.ProtectedCustomerEnrollmentPakeKey
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.crypto.PublicKey
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.*
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.post
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class RelationshipsF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : RelationshipsF8eClient {
  override suspend fun createRelationship(
    account: FullAccount,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    trustedContactAlias: TrustedContactAlias,
    protectedCustomerEnrollmentPakeKey: PublicKey<ProtectedCustomerEnrollmentPakeKey>,
    roles: Set<TrustedContactRole>,
  ): Result<Invitation, NetworkingError> {
    return f8eHttpClient.authenticated(
      f8eEnvironment = account.config.f8eEnvironment,
      accountId = account.accountId,
      hwFactorProofOfPossession = hardwareProofOfPossession
    ).bodyResult<CreateRelationshipInvitationResponseBody> {
      post("/api/accounts/${account.accountId.serverId}/relationships") {
        withDescription("Create relationship invitation")
        setRedactedBody(
          CreateRelationshipInvitationRequestBody(
            trustedContactAlias = trustedContactAlias,
            protectedCustomerEnrollmentPakeKey = protectedCustomerEnrollmentPakeKey,
            roles = roles.toList()
          )
        )
      }
    }.map { it.invitation.toInvitation() }
  }
}

@Serializable
private data class CreateRelationshipInvitationRequestBody(
  @SerialName("trusted_contact_alias")
  val trustedContactAlias: TrustedContactAlias,
  @SerialName("protected_customer_enrollment_pake_pubkey")
  val protectedCustomerEnrollmentPakeKey: PublicKey<ProtectedCustomerEnrollmentPakeKey>,
  @SerialName("trusted_contact_roles")
  val roles: List<TrustedContactRole>,
) : RedactedRequestBody

@Serializable
private data class CreateRelationshipInvitationResponseBody(
  val invitation: CreateRelationshipInvitation,
) : RedactedResponseBody

@Serializable
private data class CreateRelationshipInvitation(
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

private fun CreateRelationshipInvitation.toInvitation() =
  Invitation(
    relationshipId = relationshipId,
    trustedContactAlias = trustedContactAlias,
    roles = roles,
    code = code,
    codeBitLength = codeBitLength,
    expiresAt = expiresAt
  )
