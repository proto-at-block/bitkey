package build.wallet.f8e.inheritance

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.challange.SignedChallenge.AppSignedChallenge
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.inheritance.BeneficiaryClaimSerializer
import build.wallet.bitkey.inheritance.InheritanceClaimId
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.flatMap
import dev.zacsweers.redacted.annotations.Unredacted
import io.ktor.client.request.put
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class LockInheritanceClaimF8eClientImpl(
  private val f8eClient: F8eHttpClient,
) : LockInheritanceClaimF8eClient {
  override suspend fun lockClaim(
    fullAccount: FullAccount,
    relationshipId: RelationshipId,
    inheritanceClaimId: InheritanceClaimId,
    signedChallenge: AppSignedChallenge,
  ): Result<BeneficiaryClaim.LockedClaim, Throwable> {
    return f8eClient.authenticated(
      f8eEnvironment = fullAccount.config.f8eEnvironment,
      accountId = fullAccount.accountId
    ).bodyResult<LockClaimResponse> {
      put("/api/accounts/${fullAccount.accountId.serverId}/recovery/inheritance/claims/${inheritanceClaimId.value}/lock") {
        withDescription("Locking Inheritance Claim")
        setRedactedBody(
          LockInheritanceClaimRequest(
            challenge = signedChallenge.challenge.data,
            relationshipId = relationshipId,
            appSignature = signedChallenge.signature
          )
        )
      }
    }.flatMap {
      when (val result = it.claim) {
        is BeneficiaryClaim.LockedClaim -> Ok(result)
        else -> Err(IllegalArgumentException("Unexpected claim type: $result"))
      }
    }
  }

  @Serializable
  private data class LockClaimResponse(
    @Serializable(with = BeneficiaryClaimSerializer::class)
    @Unredacted
    val claim: BeneficiaryClaim,
  ) : RedactedResponseBody

  @Serializable
  private data class LockInheritanceClaimRequest(
    @SerialName("recovery_relationship_id")
    val relationshipId: RelationshipId,
    @SerialName("app_signature")
    val appSignature: String,
    @Unredacted
    val challenge: String,
  ) : RedactedRequestBody
}
