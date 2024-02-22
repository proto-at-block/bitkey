package build.wallet.f8e.mobilepay

import build.wallet.configuration.FiatMobilePayConfiguration
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.currency.FiatCurrency
import com.github.michaelbull.result.Result

interface FiatMobilePayConfigurationService {
  /**
   * Retrieves a map of [FiatCurrency] to [FiatMobilePayConfiguration] definitions
   * for the current supported fiat currencies from f8e.
   */
  suspend fun getFiatMobilePayConfigurations(
    f8eEnvironment: F8eEnvironment,
  ): Result<Map<FiatCurrency, FiatMobilePayConfiguration>, NetworkingError>
}
