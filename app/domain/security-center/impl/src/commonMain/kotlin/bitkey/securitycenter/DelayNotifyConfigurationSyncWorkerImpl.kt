package bitkey.securitycenter

import build.wallet.account.AccountService
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.worker.RunStrategy
import kotlinx.coroutines.flow.first

@BitkeyInject(AppScope::class)
class DelayNotifyConfigurationSyncWorkerImpl(
  private val accountService: AccountService,
  private val delayNotifyConfigurationService: DelayNotifyConfigurationService,
  delayNotifyConfigurationSyncFrequency: DelayNotifyConfigurationSyncFrequency,
) : DelayNotifyConfigurationSyncWorker {
  override val runStrategy: Set<RunStrategy> = setOf(
    RunStrategy.Startup(),
    RunStrategy.Periodic(interval = delayNotifyConfigurationSyncFrequency.value)
  )

  override suspend fun executeWork() {
    val activeAccount = accountService.activeAccount().first() as? FullAccount
    if (activeAccount != null) {
      delayNotifyConfigurationService.syncDelayNotifyPeriod(activeAccount)
    }
  }
}
