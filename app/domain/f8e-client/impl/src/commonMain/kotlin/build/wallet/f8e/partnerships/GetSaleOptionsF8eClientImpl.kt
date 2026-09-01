package build.wallet.f8e.partnerships

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.money.currency.FiatCurrency
import build.wallet.partnerships.SaleOptions
import build.wallet.platform.settings.CountryCodeGuesser
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class GetSaleOptionsF8eClientImpl(
  private val countryCodeGuesser: CountryCodeGuesser,
  private val f8eHttpClient: F8eHttpClient,
) : GetSaleOptionsF8eClient {
  override suspend fun saleOptions(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    currency: FiatCurrency,
  ): Result<SaleOptions, NetworkingError> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<SaleOptionsResponseBody> {
        get("/api/partnerships/sales/options") {
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          parameter("country", countryCodeGuesser.countryCode().uppercase())
          parameter("fiat_currency", currency.textCode.code.uppercase())
          withDescription("Get partnerships sale options")
        }
      }
      .map { it.saleOptions }
  }
}

@Serializable
private data class SaleOptionsResponseBody(
  @SerialName("sale_options")
  val saleOptions: SaleOptions,
) : RedactedResponseBody
