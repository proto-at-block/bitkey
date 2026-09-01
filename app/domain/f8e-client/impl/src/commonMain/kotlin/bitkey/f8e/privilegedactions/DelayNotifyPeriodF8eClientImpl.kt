package bitkey.f8e.privilegedactions

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.applyTo
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import dev.zacsweers.redacted.annotations.Unredacted
import io.ktor.client.request.get
import io.ktor.client.request.put
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class DelayNotifyPeriodF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : DelayNotifyPeriodF8eClient {
  override suspend fun getDelayNotifyPeriod(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<Int, NetworkingError> =
    f8eHttpClient.authenticated()
      .bodyResult<GetDelayNotifyPeriodResponse> {
        get("/api/accounts/${fullAccountId.serverId}/delay-notify-period") {
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          withDescription("Get delay notify period")
        }
      }
      .map { it.delayPeriodDays }
      .logFailure { "Failed to get delay notify period" }

  override suspend fun setDelayNotifyPeriod(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    delayPeriodDays: Int,
    proof: PrivilegedActionProof?,
  ): Result<Int, NetworkingError> =
    f8eHttpClient.authenticated()
      .bodyResult<SetDelayNotifyPeriodResponse> {
        put("/api/accounts/${fullAccountId.serverId}/delay-notify-period") {
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          withDescription("Set delay notify period")
          proof.applyTo(this)
          setRedactedBody(SetDelayNotifyPeriodRequest(delayPeriodDays))
        }
      }
      .map { it.delayPeriodDays }
      .logFailure { "Failed to set delay notify period" }
}

@Serializable
private data class GetDelayNotifyPeriodResponse(
  @Unredacted
  @SerialName("delay_period_days")
  val delayPeriodDays: Int,
) : RedactedResponseBody

@Serializable
private data class SetDelayNotifyPeriodRequest(
  @SerialName("delay_period_days")
  val delayPeriodDays: Int,
) : RedactedRequestBody

@Serializable
private data class SetDelayNotifyPeriodResponse(
  @Unredacted
  @SerialName("delay_period_days")
  val delayPeriodDays: Int,
) : RedactedResponseBody
