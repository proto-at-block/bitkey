package build.wallet.fwup

import bitkey.account.AccountConfigService
import bitkey.account.DefaultAccountConfig
import bitkey.account.FullAccountConfig
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.di.Impl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Provider that returns the appropriate [FirmwareDownloader] based on whether
 * the account is configured to use fake hardware or real hardware.
 */
interface FirmwareDownloaderProvider {
  fun get(): StateFlow<FirmwareDownloader>
}

@BitkeyInject(AppScope::class)
class FirmwareDownloaderProviderImpl(
  @Impl private val firmwareDownloaderImpl: FirmwareDownloader,
  @Fake private val firmwareDownloaderFake: FirmwareDownloader,
  accountConfigService: AccountConfigService,
  appCoroutineScope: CoroutineScope,
) : FirmwareDownloaderProvider {
  private val firmwareDownloaderFlow: StateFlow<FirmwareDownloader> =
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
      .map { isFake -> if (isFake) firmwareDownloaderFake else firmwareDownloaderImpl }
      .stateIn(
        scope = appCoroutineScope,
        started = SharingStarted.Eagerly,
        initialValue = firmwareDownloaderImpl
      )

  override fun get(): StateFlow<FirmwareDownloader> = firmwareDownloaderFlow
}
