package bitkey.account

import build.wallet.account.AccountService
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.account.AccountStatus.LiteAccountUpgradingToFullAccount
import build.wallet.account.AccountStatus.OnboardingAccount
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.DefaultAccountConfigEntity
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.logging.logError
import build.wallet.logging.logFailure
import build.wallet.mapResult
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@BitkeyInject(AppScope::class)
class AccountConfigServiceImpl(
  appCoroutineScope: CoroutineScope,
  private val databaseProvider: BitkeyDatabaseProvider,
  private val appVariant: AppVariant,
  accountService: AccountService,
) : AccountConfigService {
  private val fallbackAppConfig by lazy {
    fallbackAppConfig(appVariant)
  }

  private val defaultConfigCache = flow {
    databaseProvider.database()
      .defaultAccountConfigQueries
      .config()
      .asFlowOfOneOrNull()
      .mapResult { it?.toConfig() ?: fallbackAppConfig }
      .mapLatest {
        if (it.isOk) {
          it.value
        } else {
          logError(throwable = it.error) {
            "Error reading app config from db, using default config."
          }
          // Fallback on default config
          fallbackAppConfig
        }
      }
      .distinctUntilChanged()
      .collect(::emit)
  }.stateIn(
    scope = appCoroutineScope,
    started = Eagerly,
    initialValue = fallbackAppConfig
  )

  override fun defaultConfig(): StateFlow<DefaultAccountConfig> = defaultConfigCache

  private val activeOrDefaultConfigCache: StateFlow<AccountConfig> = combine(
    accountService.accountStatus(),
    defaultConfigCache
  ) { accountStatusResult, defaultConfig ->
    when (val accountStatus = accountStatusResult.get()) {
      is ActiveAccount -> accountStatus.account.config
      is LiteAccountUpgradingToFullAccount -> accountStatus.onboardingAccount.config
      is OnboardingAccount -> accountStatus.account.config
      else -> defaultConfig
    }
  }.stateIn(
    scope = appCoroutineScope,
    started = Eagerly,
    initialValue = fallbackAppConfig
  )

  override fun activeOrDefaultConfig(): StateFlow<AccountConfig> = activeOrDefaultConfigCache

  override suspend fun setBitcoinNetworkType(value: BitcoinNetworkType): Result<Unit, Error> {
    return updateDefaultAppConfig { it.copy(bitcoinNetworkType = value) }
  }

  override suspend fun setIsHardwareFake(value: Boolean): Result<Unit, Error> {
    return updateDefaultAppConfig { it.copy(isHardwareFake = value) }
  }

  override suspend fun setIsTestAccount(value: Boolean): Result<Unit, Error> {
    return updateDefaultAppConfig { it.copy(isTestAccount = value) }
  }

  override suspend fun setUsingSocRecFakes(value: Boolean): Result<Unit, Error> {
    return updateDefaultAppConfig { it.copy(isUsingSocRecFakes = value) }
  }

  override suspend fun setF8eEnvironment(value: F8eEnvironment): Result<Unit, Error> {
    return updateDefaultAppConfig { it.copy(f8eEnvironment = value) }
  }

  override suspend fun setSkipCloudBackupOnboarding(value: Boolean): Result<Unit, Error> {
    return updateDefaultAppConfig { it.copy(skipCloudBackupOnboarding = value) }
  }

  override suspend fun setSkipNotificationsOnboarding(value: Boolean): Result<Unit, Error> {
    return updateDefaultAppConfig { it.copy(skipNotificationsOnboarding = value) }
  }

  override suspend fun setDelayNotifyDuration(value: Duration?): Result<Unit, Error> {
    return updateDefaultAppConfig { it.copy(delayNotifyDuration = value) }
  }

  override suspend fun enableDemoMode(): Result<Unit, Error> {
    return updateDefaultAppConfig(bypassCustomerAppValidation = true) {
      it.copy(
        isHardwareFake = true,
        isTestAccount = true
      )
    }
  }

  override suspend fun disableDemoMode(): Result<Unit, Error> {
    return updateDefaultAppConfig(bypassCustomerAppValidation = true) {
      it.copy(
        isHardwareFake = false,
        isTestAccount = false
      )
    }
  }

  private suspend fun updateDefaultAppConfig(
    bypassCustomerAppValidation: Boolean = false,
    block: (currentConfig: DefaultAccountConfig) -> DefaultAccountConfig,
  ): Result<Unit, Error> {
    check(bypassCustomerAppValidation || appVariant != Customer) {
      "Not supposed to override default app config in Customer builds."
    }
    return databaseProvider.database().awaitTransaction {
      val currentOptions = defaultAccountConfigQueries.config().executeAsOneOrNull()
        ?.toConfig()
        ?: fallbackAppConfig

      val updatedConfig = block(currentOptions)
      defaultAccountConfigQueries.setConfig(
        bitcoinNetworkType = updatedConfig.bitcoinNetworkType,
        fakeHardware = updatedConfig.isHardwareFake,
        f8eEnvironment = updatedConfig.f8eEnvironment,
        isTestAccount = updatedConfig.isTestAccount,
        isUsingSocRecFakes = updatedConfig.isUsingSocRecFakes,
        delayNotifyDuration = updatedConfig.delayNotifyDuration,
        skipNotificationsOnboarding = updatedConfig.skipNotificationsOnboarding,
        skipCloudBackupOnboarding = updatedConfig.skipCloudBackupOnboarding
      )
    }.logFailure { "Error updating app config in db." }
  }
}

private fun DefaultAccountConfigEntity.toConfig(): DefaultAccountConfig {
  return DefaultAccountConfig(
    bitcoinNetworkType = bitcoinNetworkType,
    isHardwareFake = fakeHardware,
    f8eEnvironment = f8eEnvironment,
    isTestAccount = isTestAccount,
    isUsingSocRecFakes = isUsingSocRecFakes,
    delayNotifyDuration = delayNotifyDuration,
    skipNotificationsOnboarding = skipNotificationsOnboarding,
    skipCloudBackupOnboarding = skipCloudBackupOnboarding
  )
}

/**
 * Determines fallback [DefaultAccountConfig] that should be used when
 */
private fun fallbackAppConfig(appVariant: AppVariant) =
  when (appVariant) {
    AppVariant.Development -> DefaultAccountConfig(
      bitcoinNetworkType = BitcoinNetworkType.SIGNET,
      isHardwareFake = true,
      f8eEnvironment = F8eEnvironment.Staging,
      isTestAccount = true,
      isUsingSocRecFakes = false,
      delayNotifyDuration = 20.seconds,
      skipNotificationsOnboarding = false,
      skipCloudBackupOnboarding = false
    )
    AppVariant.Alpha -> DefaultAccountConfig(
      bitcoinNetworkType = BitcoinNetworkType.BITCOIN,
      isHardwareFake = false,
      f8eEnvironment = F8eEnvironment.Production,
      isTestAccount = true,
      isUsingSocRecFakes = false,
      delayNotifyDuration = 20.seconds,
      skipNotificationsOnboarding = false,
      skipCloudBackupOnboarding = false
    )
    AppVariant.Team -> DefaultAccountConfig(
      bitcoinNetworkType = BitcoinNetworkType.BITCOIN,
      isHardwareFake = false,
      f8eEnvironment = F8eEnvironment.Production,
      isTestAccount = true,
      isUsingSocRecFakes = false,
      delayNotifyDuration = 20.seconds,
      skipNotificationsOnboarding = false,
      skipCloudBackupOnboarding = false
    )
    Customer -> DefaultAccountConfig(
      bitcoinNetworkType = BitcoinNetworkType.BITCOIN,
      isHardwareFake = false,
      f8eEnvironment = F8eEnvironment.Production,
      isTestAccount = false,
      isUsingSocRecFakes = false,
      delayNotifyDuration = null,
      skipNotificationsOnboarding = false,
      skipCloudBackupOnboarding = false
    )
    AppVariant.Emergency -> DefaultAccountConfig(
      bitcoinNetworkType = BitcoinNetworkType.BITCOIN,
      isHardwareFake = false,
      f8eEnvironment = F8eEnvironment.ForceOffline,
      isTestAccount = false,
      isUsingSocRecFakes = false,
      delayNotifyDuration = null,
      skipNotificationsOnboarding = false,
      skipCloudBackupOnboarding = false
    )
  }
