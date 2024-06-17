package build.wallet.configuration

import build.wallet.keybox.config.TemplateFullAccountConfigDao
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import com.github.michaelbull.result.onSuccess
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Implementation note: this services implements both [MobilePayFiatConfigService] and
 * [MobilePayFiatConfigSyncWorker] interfaces. This is so that [MobilePayFiatConfigService] can be
 * used by other services without being exposed to the sync logic, and [MobilePayFiatConfigSyncWorker]
 * can be injected into the sync worker executor easily.
 */
class MobilePayFiatConfigServiceImpl(
  private val mobilePayFiatConfigRepository: MobilePayFiatConfigRepository,
  // TODO(W-6665): extract dao usage into its own domain service
  private val templateFullAccountConfigDao: TemplateFullAccountConfigDao,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
) : MobilePayFiatConfigService, MobilePayFiatConfigSyncWorker {
  /**
   * In-memory cache for current Mobile Pay fiat configuration.
   * Mirrors the config based on latest fiat currency preference and available configs
   * from the repository.
   */
  private val configCache = MutableStateFlow(MobilePayFiatConfig.USD)

  override val config: StateFlow<MobilePayFiatConfig> get() = configCache

  /**
   * Fetches the latest Mobile Pay fiat configuration from f8e and updates local database.
   */
  override suspend fun execute() {
    coroutineScope {
      launch {
        // Combine latest fiat currency preference with available configs to update the cache.
        combine(
          mobilePayFiatConfigRepository.configs,
          fiatCurrencyPreferenceRepository.fiatCurrencyPreference
        ) { configs, fiatCurrency ->
          println("fiatCurrency: $fiatCurrency")
          configs[fiatCurrency]
            // If we don't have a config for the selected fiat currency,
            // use the one with hardcoded defaults
            ?: MobilePayFiatConfig.defaultForCurrency(fiatCurrency)
        }.collect(configCache)
      }

      launch {
        // Fetch configs from f8e for the environment currently used by the app.
        // Will fetch configs on app start as well as when different environment is selected in debug menu.
        templateFullAccountConfigDao.config()
          .collectLatest { config ->
            config.onSuccess {
              mobilePayFiatConfigRepository.fetchAndUpdateConfigs(it.f8eEnvironment)
            }
          }
      }
    }
  }

  /**
   * Use a config with a max limit value of 200 ($200) as a default in the unexpected case we don't
   * have a stored default config or config from the server for the limit fiat currency
   */
  private fun MobilePayFiatConfig.Companion.defaultForCurrency(fiatCurrency: FiatCurrency) =
    MobilePayFiatConfig(
      minimumLimit = FiatMoney(fiatCurrency, 0.toBigDecimal()),
      maximumLimit = FiatMoney(fiatCurrency, 200.toBigDecimal()),
      snapValues = emptyMap()
    )
}
