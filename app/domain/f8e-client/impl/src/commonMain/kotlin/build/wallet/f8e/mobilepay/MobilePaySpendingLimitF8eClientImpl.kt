package build.wallet.f8e.mobilepay

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.MobilePayErrorCode
import bitkey.f8e.error.toF8eError
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
import build.wallet.ktor.result.EmptyRequestBody
import build.wallet.ktor.result.EmptyResponseBody
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.catching
import build.wallet.ktor.result.setRedactedBody
import build.wallet.limit.SpendingLimit
import build.wallet.mapUnit
import build.wallet.platform.settings.Locale
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import io.ktor.client.request.delete
import io.ktor.client.request.put
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class MobilePaySpendingLimitF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
  private val clock: Clock,
) : MobilePaySpendingLimitF8eClient {
  override suspend fun setSpendingLimit(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    limit: SpendingLimit,
    proof: PrivilegedActionProof,
    locale: Locale,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<EmptyResponseBody> {
        put("/api/accounts/${fullAccountId.serverId}/mobile-pay") {
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          proof.applyTo(this)
          setRedactedBody(
            RequestBody(
              limit = limit.toServerSpendingLimit(clock),
              locale = locale.toBcp47()
            )
          )
        }
      }
      .mapUnit()
  }

  override suspend fun disableMobilePay(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    proof: PrivilegedActionProof?,
  ): Result<Unit, F8eError<MobilePayErrorCode>> {
    return f8eHttpClient.authenticated()
      .catching {
        delete("/api/accounts/${fullAccountId.serverId}/mobile-pay") {
          withDescription("Disable Mobile Pay")
          withAccountId(fullAccountId)
          withEnvironment(f8eEnvironment)
          proof.applyTo(this)
          setRedactedBody(EmptyRequestBody)
        }
      }.mapUnit()
      .mapError { it.toF8eError<MobilePayErrorCode>() }
  }

  @Serializable
  private data class RequestBody(
    val limit: ServerSpendingLimitDTO?,
    val locale: String,
  ) : RedactedRequestBody

  @Serializable
  data object DisableMobilePayResponse
}
