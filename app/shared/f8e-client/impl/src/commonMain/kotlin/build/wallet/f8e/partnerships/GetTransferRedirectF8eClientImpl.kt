package build.wallet.f8e.partnerships

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.ktor.result.*
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class GetTransferRedirectF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : GetTransferRedirectF8eClient {
  override suspend fun getTransferRedirect(
    fullAccountId: FullAccountId,
    address: BitcoinAddress,
    f8eEnvironment: F8eEnvironment,
    partner: String,
  ): Result<GetTransferRedirectF8eClient.Success, NetworkingError> {
    return f8eHttpClient
      .authenticated(
        accountId = fullAccountId,
        f8eEnvironment = f8eEnvironment
      )
      .bodyResult<ResponseBody> {
        post("/api/partnerships/transfers/redirects") {
          setRedactedBody(
            RequestBody(
              address.address,
              partner
            )
          )
        }
      }
      .map { body ->
        GetTransferRedirectF8eClient.Success(
          body.redirectInfo
        )
      }
    // W-4117 - we do not log on failure as partnerships activity should not be logged
  }

  @Serializable
  private data class RequestBody(
    val address: String,
    val partner: String,
  ) : RedactedRequestBody

  @Serializable
  private data class ResponseBody(
    @SerialName("redirect_info")
    val redirectInfo: RedirectInfo,
  ) : RedactedResponseBody
}
