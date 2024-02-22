package build.wallet.f8e.money

import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.bodyResult
import build.wallet.logging.logNetworkFailure
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.code.IsoCurrencyTextCode
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class FiatCurrencyDefinitionServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
) : FiatCurrencyDefinitionService {
  override suspend fun getCurrencyDefinitions(
    f8eEnvironment: F8eEnvironment,
  ): Result<List<FiatCurrency>, NetworkingError> {
    return f8eHttpClient.unauthenticated(f8eEnvironment)
      .bodyResult<CurrenciesResponse> {
        get("/api/exchange-rates/currencies")
      }
      .map { body -> body.supportedCurrencies.map { it.toFiatCurrency() } }
      .logNetworkFailure { "Failed to get fiat currencies" }
  }
}

@Serializable
private data class CurrenciesResponse(
  @SerialName("supported_currencies")
  val supportedCurrencies: List<SupportedFiatCurrencyDTO>,
)

@Serializable
private data class SupportedFiatCurrencyDTO(
  val currency: CurrencyDTO,
  @SerialName("fiat_display_configuration")
  val fiatDisplayConfiguration: FiatDisplayConfigurationDTO,
)

@Serializable
private data class CurrencyDTO(
  @SerialName("text_code")
  val textCode: String,
  @SerialName("unit_symbol")
  val unitSymbol: String,
  @SerialName("fractional_digits")
  val fractionalDigits: Int,
)

@Serializable
private data class FiatDisplayConfigurationDTO(
  val name: String,
  @SerialName("display_country_code")
  val displayCountryCode: String,
)

private fun SupportedFiatCurrencyDTO.toFiatCurrency() =
  FiatCurrency(
    textCode = IsoCurrencyTextCode(currency.textCode),
    unitSymbol = currency.unitSymbol,
    fractionalDigits = currency.fractionalDigits,
    displayConfiguration =
      FiatCurrency.DisplayConfiguration(
        name = fiatDisplayConfiguration.name,
        displayCountryCode = fiatDisplayConfiguration.displayCountryCode
      )
  )
