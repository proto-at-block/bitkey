package build.wallet.f8e.inheritance

import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.inheritance.BeneficiaryClaimSerializer
import build.wallet.bitkey.inheritance.InheritanceClaimId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
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
import io.ktor.client.request.put
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class CompleteInheritanceClaimF8eClientImpl(
  private val f8eClient: F8eHttpClient,
) : CompleteInheritanceClaimF8eClient {
  override suspend fun completeInheritanceClaim(
    fullAccount: FullAccount,
    claimId: InheritanceClaimId,
    psbt: Psbt,
  ): Result<BeneficiaryClaim.CompleteClaim, Throwable> {
    return f8eClient.authenticated(
      f8eEnvironment = fullAccount.config.f8eEnvironment,
      accountId = fullAccount.accountId
    ).bodyResult<CompleteClaimResponse> {
      put("/api/accounts/${fullAccount.accountId.serverId}/recovery/inheritance/claims/${claimId.value}/complete") {
        withDescription("Complete Inheritance Claim")
        setRedactedBody(
          CompleteClaimRequest(
            psbt = psbt.base64
          )
        )
      }
    }.flatMap {
      when (val result = it.claim) {
        is BeneficiaryClaim.CompleteClaim -> Ok(result)
        else -> Err(IllegalArgumentException("Unexpected claim type: $result"))
      }
    }
  }

  @Serializable
  data class CompleteClaimResponse(
    @Serializable(with = BeneficiaryClaimSerializer::class)
    val claim: BeneficiaryClaim,
  ) : RedactedResponseBody

  @Serializable
  data class CompleteClaimRequest(
    val psbt: String,
  ) : RedactedRequestBody
}
