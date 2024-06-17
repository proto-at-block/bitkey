package build.wallet.f8e.mobilepay

import build.wallet.configuration.MobilePayFiatConfig
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.currency.FiatCurrency
import com.github.michaelbull.result.Result

interface MobilePayFiatConfigF8eClient {
  /**
   * Retrieves a map of [FiatCurrency] to [MobilePayFiatConfig] definitions
   * for the current supported fiat currencies from f8e.
   */
  suspend fun getConfigs(
    f8eEnvironment: F8eEnvironment,
  ): Result<Map<FiatCurrency, MobilePayFiatConfig>, NetworkingError>
}
