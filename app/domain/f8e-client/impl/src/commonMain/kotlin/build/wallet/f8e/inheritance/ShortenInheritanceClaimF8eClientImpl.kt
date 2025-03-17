package build.wallet.f8e.inheritance

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.inheritance.InheritanceClaimId
import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.setRedactedBody
import com.github.michaelbull.result.Result
import dev.zacsweers.redacted.annotations.Unredacted
import io.ktor.client.request.put
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@BitkeyInject(AppScope::class, boundTypes = [ShortenInheritanceClaimF8eClient::class])
class ShortenInheritanceClaimF8eClientImpl(
  private val f8eClient: F8eHttpClient,
) : ShortenInheritanceClaimF8eClient {
  override suspend fun shortenClaim(
    fullAccount: FullAccount,
    claimId: InheritanceClaimId,
    delay: Duration,
  ): Result<Unit, Throwable> {
    return catchingResult {
      f8eClient.authenticated()
        .put("/api/accounts/${fullAccount.accountId.serverId}/recovery/inheritance/claims/${claimId.value}/shorten") {
          withDescription("Shortening Inheritance Claim")
          withEnvironment(fullAccount.config.f8eEnvironment)
          withAccountId(fullAccount.accountId)
          setRedactedBody(
            ShortenClaimRequest(
              delayPeriodSeconds = delay.inWholeSeconds
            )
          )
        }
    }
  }

  @Serializable
  private data class ShortenClaimRequest(
    @SerialName("delay_period_seconds")
    @Unredacted
    val delayPeriodSeconds: Long,
  ) : RedactedRequestBody
}
