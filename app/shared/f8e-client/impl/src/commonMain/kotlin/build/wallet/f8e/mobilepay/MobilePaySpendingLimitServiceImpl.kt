package build.wallet.f8e.mobilepay

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.MobilePayErrorCode
import build.wallet.f8e.error.logF8eFailure
import build.wallet.f8e.error.toF8eError
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.catching
import build.wallet.limit.SpendingLimit
import build.wallet.mapUnit
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import io.ktor.client.request.delete
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable

class MobilePaySpendingLimitServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
) : MobilePaySpendingLimitService {
  override suspend fun setSpendingLimit(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    limit: SpendingLimit,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient
      .authenticated(
        f8eEnvironment = f8eEnvironment,
        accountId = fullAccountId,
        hwFactorProofOfPossession = hwFactorProofOfPossession
      )
      .bodyResult<ResponseBody> {
        put("/api/accounts/${fullAccountId.serverId}/mobile-pay") {
          setBody(
            RequestBody(
              limit = limit.toServerSpendingLimit()
            )
          )
        }
      }
      .mapUnit()
  }

  override suspend fun disableMobilePay(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<Unit, F8eError<MobilePayErrorCode>> {
    return f8eHttpClient.authenticated(f8eEnvironment, fullAccountId)
      .catching {
        delete("/api/accounts/${fullAccountId.serverId}/mobile-pay") {
          setBody("{}")
        }
      }.mapUnit()
      .mapError { it.toF8eError<MobilePayErrorCode>() }
      .logF8eFailure { "Failed to disable Mobile Pay" }
  }

  @Serializable
  private data class RequestBody(
    val limit: ServerSpendingLimitDTO?,
  )

  @Serializable
  data object ResponseBody

  @Serializable
  data object DisableMobilePayResponse
}
