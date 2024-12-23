package build.wallet.f8e.partnerships

import build.wallet.bitcoin.address.BitcoinAddressService
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.partnerships.GetSellRedirectF8eClient.Success
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import io.ktor.client.request.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class GetSellRedirectF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
  private val bitcoinAddressService: BitcoinAddressService,
) : GetSellRedirectF8eClient {
  override suspend fun sellRedirect(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    fiatAmount: FiatMoney,
    bitcoinAmount: BitcoinMoney,
    partner: String,
  ): Result<Success, Error> =
    coroutineBinding {
      val address = bitcoinAddressService.generateAddress()
        .mapError { Error("Error generating bitcoin address.") }
        .bind()

      f8eHttpClient
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
                cryptoAmount = bitcoinAmount.value.doubleValue(exactRequired = false),
                cryptoCurrency = bitcoinAmount.currency.textCode.code.uppercase(),
                partner = partner,
                refundAddress = address.address
              )
            )
          }
        }
        .map { body ->
          Success(
            body.redirectInfo
          )
        }
        .bind()
    }

  @Serializable
  private data class RequestBody(
    @SerialName("fiat_amount")
    val fiatAmount: Double,
    @SerialName("fiat_currency")
    val fiatCurrency: String,
    @SerialName("crypto_amount")
    val cryptoAmount: Double,
    @SerialName("crypto_currency")
    val cryptoCurrency: String,
    val partner: String,
    @SerialName("refund_address")
    val refundAddress: String? = null,
  ) : RedactedRequestBody

  @Serializable
  private data class ResponseBody(
    @SerialName("redirect_info")
    val redirectInfo: RedirectInfo,
  ) : RedactedResponseBody
}
