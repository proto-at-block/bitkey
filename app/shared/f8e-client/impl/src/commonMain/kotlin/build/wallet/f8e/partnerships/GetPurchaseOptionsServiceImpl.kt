package build.wallet.f8e.partnerships

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.bodyResult
import build.wallet.logging.logNetworkFailure
import build.wallet.money.currency.FiatCurrency
import build.wallet.platform.settings.CountryCodeGuesser
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class GetPurchaseOptionsServiceImpl(
  private val countryCodeGuesser: CountryCodeGuesser,
  private val f8eHttpClient: F8eHttpClient,
) : GetPurchaseOptionsService {
  override suspend fun purchaseOptions(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    currency: FiatCurrency,
  ): Result<PurchaseOptions, NetworkingError> {
    return f8eHttpClient
      .authenticated(
        accountId = fullAccountId,
        f8eEnvironment = f8eEnvironment
      )
      .bodyResult<PurchaseOptionsResponseBody> {
        get("/api/partnerships/purchases/options") {
          parameter("country", countryCodeGuesser.countryCode().uppercase())
          parameter("fiat_currency", currency.textCode.code.uppercase())
        }
      }
      .map { it.purchaseOptions }
      .logNetworkFailure { "Failed to get partnerships purchase options" }
  }
}

@Serializable
private data class PurchaseOptionsResponseBody(
  @SerialName("purchase_options")
  val purchaseOptions: PurchaseOptions,
)
