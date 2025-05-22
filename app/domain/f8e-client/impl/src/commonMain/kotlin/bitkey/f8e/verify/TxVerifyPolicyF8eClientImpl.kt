package bitkey.f8e.verify

import bitkey.verification.TxVerificationPolicy
import bitkey.verification.VerificationThreshold
import build.wallet.bitkey.f8e.FullAccountId
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
import io.ktor.client.request.put
import kotlinx.serialization.Serializable

class TxVerifyPolicyF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : TxVerifyPolicyF8eClient {
  override suspend fun setPolicy(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    threshold: VerificationThreshold,
  ): Result<TxVerificationPolicy, Throwable> {
    return f8eHttpClient.authenticated()
      .bodyResult<Response> {
        put("/api/accounts/${fullAccountId.serverId}/tx-verify/policy") {
          withDescription("Set Tx Verification Policy")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          setRedactedBody(
            Request(
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
        TxVerificationPolicy(
          id = TxVerificationPolicy.Id(response.id),
          threshold = threshold
        )
      }
  }
}

@Serializable
private data class Request(
  @Unredacted
  val state: VerificationState,
  val threshold: Money?,
) : RedactedRequestBody

@Serializable
private data class Response(
  @Unredacted
  val id: String,
) : RedactedResponseBody

@Serializable
private enum class VerificationState {
  DISABLED,
  THRESHOLD,
  ALWAYS,
}
