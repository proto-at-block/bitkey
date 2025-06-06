package bitkey.f8e.verify

import bitkey.verification.TxVerificationPolicy
import bitkey.verification.VerificationThreshold
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import build.wallet.money.Money
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import dev.zacsweers.redacted.annotations.Unredacted
import io.ktor.client.request.get
import io.ktor.client.request.put
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class TxVerifyPolicyF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : TxVerifyPolicyF8eClient {
  override suspend fun setPolicy(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    threshold: VerificationThreshold,
  ): Result<TxVerificationPolicy.DelayNotifyAuthorization?, Error> {
    return f8eHttpClient.authenticated()
      .bodyResult<PolicyChangeResponse> {
        put("/api/accounts/${fullAccountId.serverId}/tx-verify/policy") {
          withDescription("Set Tx Verification Policy")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          setRedactedBody(
            PolicyChangeRequest(
              state = when (threshold) {
                VerificationThreshold.Disabled -> VerificationState.DISABLED
                VerificationThreshold.Always -> VerificationState.ALWAYS
                else -> VerificationState.THRESHOLD
              },
              threshold = threshold.amount.takeIf {
                threshold != VerificationThreshold.Always
              }
            )
          )
        }
      }.map { response ->
        if (response.id == null || response.strategy == null) {
          null
        } else {
          TxVerificationPolicy.DelayNotifyAuthorization(
            id = TxVerificationPolicy.DelayNotifyAuthorization.AuthId(response.id),
            delayEndTime = response.strategy.endTime,
            cancellationToken = response.strategy.cancellationToken,
            completionToken = response.strategy.completionToken
          )
        }
      }
  }

  override suspend fun getPolicy(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<VerificationThreshold, Error> {
    return f8eHttpClient.authenticated()
      .bodyResult<ThresholdResponse> {
        get("/api/accounts/${fullAccountId.serverId}/tx-verify/policy") {
          withDescription("Get Tx Verification Policy")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
        }
      }.map { response ->
        when (response.threshold) {
          null -> VerificationThreshold.Disabled
          else -> VerificationThreshold.Enabled(response.threshold)
        }
      }
  }
}

@Serializable
private data class PolicyChangeRequest(
  @Unredacted
  val state: VerificationState,
  val threshold: Money?,
) : RedactedRequestBody

@Serializable
private data class PolicyChangeResponse(
  @Unredacted
  val id: String? = null,
  @SerialName("authorization_strategy")
  val strategy: AuthStrategy? = null,
) : RedactedResponseBody {
  @Serializable
  data class AuthStrategy(
    @SerialName("delay_end_time")
    val endTime: Instant,
    @SerialName("cancellation_token")
    val cancellationToken: String,
    @SerialName("completion_token")
    val completionToken: String,
  )
}

@Serializable
private data class ThresholdResponse(
  val threshold: Money?,
) : RedactedResponseBody

@Serializable
private enum class VerificationState {
  DISABLED,
  THRESHOLD,
  ALWAYS,
}
