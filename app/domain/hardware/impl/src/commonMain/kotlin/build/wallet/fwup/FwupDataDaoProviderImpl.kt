package build.wallet.fwup

import bitkey.account.AccountConfigService
import bitkey.account.DefaultAccountConfig
import bitkey.account.FullAccountConfig
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.di.Impl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@BitkeyInject(AppScope::class)
class FwupDataDaoProviderImpl(
  @Impl private val fwupDataDaoImpl: FwupDataDao,
  @Fake private val fwupDataDaoFake: FwupDataDao,
  private val accountConfigService: AccountConfigService,
  private val appCoroutineScope: CoroutineScope,
) : FwupDataDaoProvider {
  private val fwupDataDao = MutableStateFlow(fwupDataDaoImpl)

  init {
    appCoroutineScope.launch {
      val isFakeHardware = accountConfigService.activeOrDefaultConfig()
        .map { config ->
          when (config) {
            is FullAccountConfig -> config.isHardwareFake
            is DefaultAccountConfig -> config.isHardwareFake
            else -> false
          }
        }.first()

      fwupDataDao.value = if (isFakeHardware) {
        fwupDataDaoFake
      } else {
        fwupDataDaoImpl
      }
    }
  }

  override suspend fun get(): FwupDataDao {
    return fwupDataDao.first()
  }
}
