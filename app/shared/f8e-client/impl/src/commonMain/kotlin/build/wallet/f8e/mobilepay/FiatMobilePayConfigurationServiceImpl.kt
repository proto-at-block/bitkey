package build.wallet.f8e.mobilepay

import build.wallet.configuration.FiatMobilePayConfiguration
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.bodyResult
import build.wallet.logging.logNetworkFailure
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.FiatCurrencyDao
import build.wallet.money.currency.USD
import build.wallet.money.currency.code.IsoCurrencyTextCode
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import com.ionspin.kotlin.bignum.integer.toBigInteger
import io.ktor.client.request.get
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class FiatMobilePayConfigurationServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
  private val fiatCurrencyDao: FiatCurrencyDao,
) : FiatMobilePayConfigurationService {
  override suspend fun getFiatMobilePayConfigurations(
    f8eEnvironment: F8eEnvironment,
  ): Result<Map<FiatCurrency, FiatMobilePayConfiguration>, NetworkingError> {
    // TODO(W-5154): Replace with below unused function when f8e endpoint is implemented
    return Ok(
      (fiatCurrencyDao.allFiatCurrencies().firstOrNull() ?: listOf(USD)).associateWith {
        it.mobilePayConfiguration
      }
    )
  }

  @Suppress("unused")
  private suspend fun realGetFiatMobilePayConfigurations(
    f8eEnvironment: F8eEnvironment,
  ): Result<Map<FiatCurrency, FiatMobilePayConfiguration>, NetworkingError> {
    return f8eHttpClient.unauthenticated(f8eEnvironment)
      .bodyResult<FiatConfigurationsResponse> {
        get("/api/mobile-pay/fiat-configurations")
      }
      .map { body ->
        body.configurations
          .mapKeys {
            fiatCurrencyDao.fiatCurrency(
              textCode = IsoCurrencyTextCode(it.key)
            ).firstOrNull()
          }
          .mapValues { entry -> entry.key?.let { entry.value.toFiatMobilePayConfiguration(it) } }
          .filterNotNull()
      }
      .logNetworkFailure { "Failed to get fiat currencies" }
  }
}

@Serializable
private data class FiatConfigurationsResponse(
  // Maps a currency text code string to the configuration
  val configurations: Map<String, FiatMobilePayConfigurationDTO>,
)

@Serializable
private data class FiatMobilePayConfigurationDTO(
  @SerialName("minimum_limit_fractional_unit_value")
  val minimumLimitFractionalUnitValue: Int,
  @SerialName("maximum_limit_fractional_unit_value")
  val maximumLimitFractionalUnitValue: Int,
  @SerialName("snap_values")
  val snapValues: Map<Int, Int>,
)

private fun FiatMobilePayConfigurationDTO.toFiatMobilePayConfiguration(currency: FiatCurrency) =
  FiatMobilePayConfiguration(
    minimumLimit =
      FiatMoney(
        currency,
        fractionalUnitAmount = minimumLimitFractionalUnitValue.toBigInteger()
      ),
    maximumLimit =
      FiatMoney(
        currency,
        fractionalUnitAmount = maximumLimitFractionalUnitValue.toBigInteger()
      ),
    snapValues =
      snapValues
        .mapKeys { FiatMoney(currency, it.key.toBigInteger()) }
        .mapValues {
          FiatMobilePayConfiguration.SnapTolerance(FiatMoney(currency, it.value.toBigInteger()))
        }
  )

private fun <K, V> Map<out K?, V?>.filterNotNull(): Map<K, V> =
  this.mapNotNull {
    it.key?.let { key ->
      it.value?.let { value ->
        key to value
      }
    }
  }.toMap()

// TODO(W-5154): Remove when f8e endpoint is implemented
val FiatCurrency.mobilePayConfiguration: FiatMobilePayConfiguration
  get() {
    fun configuration(maximumLimit: Int) =
      FiatMobilePayConfiguration(
        minimumLimit = FiatMoney(this, 0.toBigDecimal()),
        maximumLimit = FiatMoney(this, maximumLimit.toBigDecimal()),
        snapValues = emptyMap()
      )

    return when (textCode.code) {
      "AUD" ->
        configuration(maximumLimit = 300)

      "CAD" ->
        configuration(maximumLimit = 300)

      "EUR" ->
        configuration(maximumLimit = 200)

      "GBP" ->
        configuration(maximumLimit = 200)

      "JPY" ->
        configuration(maximumLimit = 30000)

      "KWD" ->
        configuration(maximumLimit = 60)

      "USD" ->
        FiatMobilePayConfiguration.USD

      else -> error("Unsupported FiatCurrency ${this.textCode.code}")
    }
  }
