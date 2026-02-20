package build.wallet.fwup

import bitkey.account.AccountConfigService
import bitkey.account.DefaultAccountConfig
import bitkey.account.FullAccountConfig
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.di.Impl
import build.wallet.platform.data.FileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Provider that returns the appropriate [FileManager] based on whether
 * the account is configured to use fake hardware or real hardware.
 */
interface FileManagerProvider {
  fun get(): StateFlow<FileManager>
}

@BitkeyInject(AppScope::class)
class FileManagerProviderImpl(
  @Impl private val fileManagerImpl: FileManager,
  @Fake private val fileManagerFake: FileManager,
  accountConfigService: AccountConfigService,
  appCoroutineScope: CoroutineScope,
) : FileManagerProvider {
  private val fileManagerFlow: StateFlow<FileManager> =
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
      .map { isFake -> if (isFake) fileManagerFake else fileManagerImpl }
      .stateIn(
        scope = appCoroutineScope,
        started = SharingStarted.Eagerly,
        initialValue = fileManagerImpl
      )

  override fun get(): StateFlow<FileManager> = fileManagerFlow
}
