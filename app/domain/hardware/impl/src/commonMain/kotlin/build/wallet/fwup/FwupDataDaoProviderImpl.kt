package build.wallet.fwup

import bitkey.account.AccountConfigService
import bitkey.account.DefaultAccountConfig
import bitkey.account.FullAccountConfig
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.di.Impl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

@BitkeyInject(AppScope::class)
class FwupDataDaoProviderImpl(
  @Impl private val fwupDataDaoImpl: FwupDataDao,
  @Fake private val fwupDataDaoFake: FwupDataDao,
  accountConfigService: AccountConfigService,
  appCoroutineScope: CoroutineScope,
) : FwupDataDaoProvider {
  private val fwupDataDaoFlow: StateFlow<FwupDataDao> =
    accountConfigService
      .activeOrDefaultConfig()
      .map { config ->
        when (config) {
          is FullAccountConfig -> config.isHardwareFake
          is DefaultAccountConfig -> config.isHardwareFake
          else -> false
        }
      }
      .distinctUntilChanged()
      .onEach { isFake ->
        // Clear firmware data when switching to real hardware.
        // This prevents mock firmware data from being applied to real hardware,
        // which could cause unexpected behavior.
        if (!isFake) fwupDataDaoImpl.clear()
      }
      .map { isFake -> if (isFake) fwupDataDaoFake else fwupDataDaoImpl }
      .stateIn(
        scope = appCoroutineScope,
        started = SharingStarted.Eagerly,
        initialValue = fwupDataDaoImpl
      )

  override fun get(): StateFlow<FwupDataDao> = fwupDataDaoFlow
}
