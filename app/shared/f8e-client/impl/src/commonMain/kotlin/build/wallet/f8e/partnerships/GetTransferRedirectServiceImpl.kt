package build.wallet.f8e.partnerships

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.bodyResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class GetTransferRedirectServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
) : GetTransferRedirectService {
  override suspend fun getTransferRedirect(
    fullAccountId: FullAccountId,
    address: BitcoinAddress,
    f8eEnvironment: F8eEnvironment,
    partner: String,
  ): Result<GetTransferRedirectService.Success, NetworkingError> {
    return f8eHttpClient
      .authenticated(
        accountId = fullAccountId,
        f8eEnvironment = f8eEnvironment
      )
      .bodyResult<ResponseBody> {
        post("/api/partnerships/transfers/redirects") {
          setBody(
            RequestBody(
              address.address,
              partner
            )
          )
        }
      }
      .map { body ->
        GetTransferRedirectService.Success(
          body.redirectInfo
        )
      }
    // W-4117 - we do not log on failure as partnerships activity should not be logged
  }

  @Serializable
  private data class RequestBody(
    val address: String,
    val partner: String,
  )

  @Serializable
  private data class ResponseBody(
    @SerialName("redirect_info")
    val redirectInfo: RedirectInfo,
  )
}
