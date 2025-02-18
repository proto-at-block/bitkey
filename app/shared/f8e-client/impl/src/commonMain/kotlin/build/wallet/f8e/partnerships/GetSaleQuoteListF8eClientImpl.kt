package build.wallet.f8e.partnerships

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.f8e.partnerships.GetSaleQuoteListF8eClient.Success
import build.wallet.ktor.result.*
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.partnerships.SaleQuote
import build.wallet.platform.settings.CountryCodeGuesser
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.post
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class GetSaleQuoteListF8eClientImpl(
  private val countryCodeGuesser: CountryCodeGuesser,
  private val f8eHttpClient: F8eHttpClient,
) : GetSaleQuoteListF8eClient {
  override suspend fun getSaleQuotes(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    cryptoAmount: BitcoinMoney,
    fiatCurrency: FiatCurrency,
  ): Result<Success, NetworkingError> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<ResponseBody> {
        post("/api/partnerships/sales/quotes") {
          withDescription("Get sale quotes")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          setRedactedBody(
            RequestBody(
              country = countryCodeGuesser.countryCode(),
              cryptoAmount = cryptoAmount.value.doubleValue(exactRequired = false),
              currency = fiatCurrency.textCode.code
            )
          )
        }
      }
      .map { body -> Success(body.quotes.toImmutableList()) }
  }

  @Serializable
  private data class RequestBody(
    val country: String,
    @SerialName("crypto_amount")
    val cryptoAmount: Double,
    @SerialName("fiat_currency")
    val currency: String,
  ) : RedactedRequestBody

  @Serializable
  private data class ResponseBody(
    val quotes: List<SaleQuote>,
  ) : RedactedResponseBody
}
