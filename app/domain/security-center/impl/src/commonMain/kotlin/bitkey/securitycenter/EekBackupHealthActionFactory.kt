package bitkey.securitycenter

import build.wallet.account.AccountService
import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.FunctionalityFeatureStates
import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.cloud.backup.health.EekBackupStatus
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.Instant

interface EekBackupHealthActionFactory {
  suspend fun create(): Flow<SecurityAction?>
}

@BitkeyInject(AppScope::class)
class EekBackupHealthActionFactoryImpl(
  private val cloudBackupHealthRepository: CloudBackupHealthRepository,
  private val accountService: AccountService,
  private val appFunctionalityService: AppFunctionalityService,
) : EekBackupHealthActionFactory {
  override suspend fun create(): Flow<SecurityAction?> {
    return combine(
      accountService.activeAccount(),
      cloudBackupHealthRepository.eekBackupStatus().filterNotNull(),
      appFunctionalityService.status
    ) { account, eekBackupStatus, featureState ->
      if (account !is FullAccount) {
        null
      } else {
        EekBackupHealthAction(
          cloudBackupStatus = eekBackupStatus,
          featureState = featureState.featureStates.cloudBackupHealth
        )
      }
    }.onStart {
      emit(
        EekBackupHealthAction(
          cloudBackupStatus = EekBackupStatus.Healthy(Instant.DISTANT_PAST),
          featureState = FunctionalityFeatureStates.FeatureState.Unavailable
        )
      )
    }
  }
}
