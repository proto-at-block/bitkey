package build.wallet.f8e.partnerships

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.partnerships.GetSellRedirectF8eClient.Success
import build.wallet.ktor.result.*
import build.wallet.money.FiatMoney
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class GetSellRedirectF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : GetSellRedirectF8eClient {
  override suspend fun sellRedirect(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    fiatAmount: FiatMoney,
    partner: String,
  ): Result<Success, NetworkingError> {
    return f8eHttpClient
      .authenticated(
        accountId = fullAccountId,
        f8eEnvironment = f8eEnvironment
      )
      .bodyResult<ResponseBody> {
        post("/api/partnerships/sales/redirects") {
          setRedactedBody(
            RequestBody(
              fiatAmount = fiatAmount.value.doubleValue(exactRequired = false),
              fiatCurrency = fiatAmount.currency.textCode.code.uppercase(),
              partner = partner
            )
          )
        }
      }
      .map { body ->
        Success(
          body.redirectInfo
        )
      }
  }

  @Serializable
  private data class RequestBody(
    @SerialName("fiat_amount")
    val fiatAmount: Double,
    @SerialName("fiat_currency")
    val fiatCurrency: String,
    val partner: String,
  ) : RedactedRequestBody

  @Serializable
  private data class ResponseBody(
    @SerialName("redirect_info")
    val redirectInfo: RedirectInfo,
  ) : RedactedResponseBody
}
