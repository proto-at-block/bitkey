package build.wallet.f8e.inheritance

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.inheritance.*
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.*
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.flatMap
import io.ktor.client.*
import io.ktor.client.request.post
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class CancelInheritanceClaimF8eClientImpl(
  private val f8eClient: F8eHttpClient,
) : CancelInheritanceClaimF8eClient {
  override suspend fun cancelClaim(
    fullAccount: FullAccount,
    inheritanceClaim: InheritanceClaim,
  ): Result<InheritanceClaim, Throwable> {
    val client = f8eClient.authenticated()

    return when (inheritanceClaim) {
      is BenefactorClaim -> executePostRequest<BenefactorCancelClaimResponse>(client, fullAccount, inheritanceClaim)
      is BeneficiaryClaim -> executePostRequest<BeneficiaryCancelClaimResponse>(client, fullAccount, inheritanceClaim)
    }.flatMap {
      when (val result = it.claim) {
        is BeneficiaryClaim.CanceledClaim -> Ok(result)
        is BenefactorClaim.CanceledClaim -> Ok(result)
        else -> Err(IllegalArgumentException("Unexpected claim type: $result"))
      }
    }
  }

  private suspend inline fun <reified R : InheritanceClaimResponse> executePostRequest(
    client: HttpClient,
    fullAccount: FullAccount,
    inheritanceClaim: InheritanceClaim,
  ): Result<R, Throwable> {
    return client.bodyResult {
      post("/api/accounts/${fullAccount.accountId.serverId}/recovery/inheritance/claims/${inheritanceClaim.claimId.value}/cancel") {
        withDescription("Canceling Inheritance Claim")
        setRedactedBody(EmptyRequestBody)
        withEnvironment(fullAccount.config.f8eEnvironment)
        withAccountId(fullAccount.accountId)
      }
    }
  }

  interface InheritanceClaimResponse : RedactedResponseBody {
    val claim: InheritanceClaim
  }

  @Serializable
  private data class BenefactorCancelClaimResponse(
    @Serializable(with = BenefactorClaimSerializer::class)
    override val claim: BenefactorClaim,
  ) : InheritanceClaimResponse

  @Serializable
  private data class BeneficiaryCancelClaimResponse(
    @Serializable(with = BeneficiaryClaimSerializer::class)
    override val claim: BeneficiaryClaim,
  ) : InheritanceClaimResponse
}
