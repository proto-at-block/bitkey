package build.wallet.f8e.inheritance

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.inheritance.BeneficiaryClaimSerializer
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
import io.ktor.client.request.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class StartInheritanceClaimF8eClientImpl(
  private val f8eClient: F8eHttpClient,
) : StartInheritanceClaimF8eClient {
  override suspend fun startInheritanceClaim(
    fullAccount: FullAccount,
    relationshipId: RelationshipId,
  ): Result<BeneficiaryClaim.PendingClaim, Throwable> {
    return f8eClient.authenticated(
      f8eEnvironment = fullAccount.config.f8eEnvironment,
      accountId = fullAccount.accountId
    ).bodyResult<StartClaimResponse> {
      post("/api/accounts/${fullAccount.accountId.serverId}/recovery/inheritance/claims") {
        withDescription("Starting Inheritance Claim")
        setRedactedBody(
          StartInheritanceClaimRequest(
            auth = StartInheritanceClaimRequest.Auth(
              appPubkey = fullAccount.keybox.activeAppKeyBundle.authKey.value,
              hardwarePubkey = fullAccount.keybox.activeHwKeyBundle.authKey.pubKey.value
            ),
            relationshipId = relationshipId
          )
        )
      }
    }.flatMap {
      when (val result = it.claim) {
        is BeneficiaryClaim.PendingClaim -> Ok(result)
        else -> Err(IllegalArgumentException("Unexpected claim type: $result"))
      }
    }
  }

  @Serializable
  private data class StartClaimResponse(
    @Serializable(with = BeneficiaryClaimSerializer::class)
    val claim: BeneficiaryClaim,
  ) : RedactedResponseBody

  @Serializable
  private data class StartInheritanceClaimRequest(
    val auth: Auth,
    @SerialName("recovery_relationship_id")
    @Unredacted
    val relationshipId: RelationshipId,
  ) : RedactedRequestBody {
    @Serializable
    data class Auth(
      @SerialName("app_pubkey")
      val appPubkey: String,
      @SerialName("hardware_pubkey")
      val hardwarePubkey: String,
    )
  }
}
