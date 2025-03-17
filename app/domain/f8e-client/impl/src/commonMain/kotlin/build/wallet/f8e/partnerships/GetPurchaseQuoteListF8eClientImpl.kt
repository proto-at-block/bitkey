package build.wallet.f8e.partnerships

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.partnerships.GetPurchaseQuoteListF8eClient.Success
import build.wallet.ktor.result.*
import build.wallet.money.FiatMoney
import build.wallet.partnerships.PurchaseQuote
import build.wallet.platform.settings.CountryCodeGuesser
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class GetPurchaseQuoteListF8eClientImpl(
  private val countryCodeGuesser: CountryCodeGuesser,
  private val f8eHttpClient: F8eHttpClient,
) : GetPurchaseQuoteListF8eClient {
  override suspend fun purchaseQuotes(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    fiatAmount: FiatMoney,
    paymentMethod: String,
  ): Result<Success, NetworkingError> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<ResponseBody> {
        post("/api/partnerships/purchases/quotes") {
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          setRedactedBody(
            RequestBody(
              country = countryCodeGuesser.countryCode().uppercase(),
              fiatAmount = fiatAmount.value.doubleValue(exactRequired = false),
              fiatCurrency = fiatAmount.currency.textCode.code.uppercase(),
              paymentMethod = paymentMethod
            )
          )
        }
      }
      .map { body -> Success(body.quotes) }
  }

  @Serializable
  private data class RequestBody(
    val country: String,
    @SerialName("fiat_amount")
    val fiatAmount: Double,
    @SerialName("fiat_currency")
    val fiatCurrency: String,
    @SerialName("payment_method")
    val paymentMethod: String,
  ) : RedactedRequestBody

  @Serializable
  private data class ResponseBody(
    val quotes: List<PurchaseQuote>,
  ) : RedactedResponseBody
}
