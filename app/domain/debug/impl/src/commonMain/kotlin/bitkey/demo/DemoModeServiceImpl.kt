package bitkey.demo

import bitkey.account.AccountConfigService
import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.W1
import build.wallet.di.W3
import build.wallet.f8e.demo.DemoModeF8eClient
import build.wallet.logging.logFailure
import build.wallet.nfc.FakeHardwareKeyStore
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.flow.first

@BitkeyInject(AppScope::class)
class DemoModeServiceImpl(
  private val accountConfigService: AccountConfigService,
  private val demoModeF8eClient: DemoModeF8eClient,
  @W1 private val w1FakeHardwareKeyStore: FakeHardwareKeyStore,
  @W3 private val w3FakeHardwareKeyStore: FakeHardwareKeyStore,
) : DemoModeService {
  override suspend fun enable(code: String): Result<Unit, Error> {
    return coroutineBinding {
      val defaultConfig = accountConfigService.defaultConfig().first()
      demoModeF8eClient.initiateDemoMode(defaultConfig.f8eEnvironment, code).bind()
      clearFakeHardwareKeyStores()
      accountConfigService.enableDemoMode().bind()
    }
  }

  override suspend fun disable(): Result<Unit, Error> =
    coroutineBinding {
      accountConfigService.disableDemoMode().bind()
    }

  /**
   * Starts each demo session with a fresh fake hardware device. Fake hardware keys are
   * persisted in encrypted storage (the Keychain on iOS), so they otherwise survive across
   * demo sessions and app reinstalls.
   *
   * A failure here is logged but does not block enabling demo mode.
   */
  private suspend fun clearFakeHardwareKeyStores() {
    catchingResult {
      w1FakeHardwareKeyStore.clear()
      w3FakeHardwareKeyStore.clear()
    }.logFailure { "Failed to clear fake hardware key stores when enabling demo mode" }
  }
}
