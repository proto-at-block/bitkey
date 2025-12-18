package build.wallet.cloud.backup.health

import build.wallet.account.AccountService
import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.FunctionalityFeatureStates
import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.CloudBackupHealthLoggingFeatureFlag
import build.wallet.platform.app.AppSessionManager
import build.wallet.platform.app.AppSessionState
import build.wallet.worker.BackgroundStrategy
import build.wallet.worker.RunStrategy
import kotlinx.coroutines.flow.*

@BitkeyInject(AppScope::class)
class CloudBackupHealthSyncWorkerImpl(
  private val accountService: AccountService,
  private val cloudBackupHealthRepository: CloudBackupHealthRepository,
  private val appFunctionalityService: AppFunctionalityService,
  appSessionManager: AppSessionManager,
  cloudBackupHealthLoggingFeatureFlag: CloudBackupHealthLoggingFeatureFlag,
) : CloudBackupHealthSyncWorker {
  override val runStrategy: Set<RunStrategy> = setOf(
    RunStrategy.OnEvent(
      observer = appSessionManager.appSessionState
        .filter { it == AppSessionState.FOREGROUND },
      backgroundStrategy = BackgroundStrategy.Skip
    ),
    RunStrategy.OnEvent(
      observer = accountService.activeAccount().distinctUntilChanged(),
      backgroundStrategy = BackgroundStrategy.Skip
    ),
    RunStrategy.OnEvent(
      observer = cloudBackupHealthLoggingFeatureFlag.flagValue(),
      backgroundStrategy = BackgroundStrategy.Skip
    )
  )

  override suspend fun executeWork() {
    val account = accountService.activeAccount().first() as? FullAccount
      ?: return

    val status = appFunctionalityService.status.first()
    if (status.featureStates.cloudBackupHealth != FunctionalityFeatureStates.FeatureState.Available) {
      return
    }

    cloudBackupHealthRepository.performSync(account)
  }
}
