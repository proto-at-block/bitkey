package bitkey.demo

import bitkey.account.AccountConfigService
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.demo.DemoModeF8eClient
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.flow.first

@BitkeyInject(AppScope::class)
class DemoModeServiceImpl(
  private val accountConfigService: AccountConfigService,
  private val demoModeF8eClient: DemoModeF8eClient,
) : DemoModeService {
  override suspend fun enable(code: String): Result<Unit, Error> {
    return return coroutineBinding {
      val defaultConfig = accountConfigService.defaultConfig().first()
      demoModeF8eClient.initiateDemoMode(defaultConfig.f8eEnvironment, code).bind()
      accountConfigService.setIsHardwareFake(value = true).bind()
      accountConfigService.setIsTestAccount(value = true).bind()
    }
  }

  override suspend fun disable(): Result<Unit, Error> =
    coroutineBinding {
      accountConfigService.setIsHardwareFake(value = false).bind()
      accountConfigService.setIsTestAccount(value = false).bind()
    }
}
