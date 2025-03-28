package bitkey.securitycenter

import build.wallet.account.AccountService
import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.cloud.backup.health.EakBackupStatus
import build.wallet.cloud.backup.health.isHealthy
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logError
import kotlinx.coroutines.flow.first

data class EakBackupHealthAction(
  private val cloudBackupStatus: EakBackupStatus,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> {
    return listOfNotNull(
      SecurityActionRecommendation.BACKUP_EAK.takeIf { !cloudBackupStatus.isHealthy() }
    )
  }

  override fun category(): SecurityActionCategory {
    return SecurityActionCategory.RECOVERY
  }
}

interface EakBackupHealthActionFactory {
  suspend fun create(): SecurityAction?
}

@BitkeyInject(AppScope::class)
class EakBackupHealthActionFactoryImpl(
  private val cloudBackupHealthRepository: CloudBackupHealthRepository,
  private val accountService: AccountService,
) : EakBackupHealthActionFactory {
  override suspend fun create(): SecurityAction? {
    val account = accountService.activeAccount().first()
    if (account !is FullAccount) {
      val message = "No active full account found when checking cloud backup. Found account: $account."
      logError { message }
      return null
    }
    val cloudBackupStatus = cloudBackupHealthRepository.performSync(account)
    return EakBackupHealthAction(cloudBackupStatus.eakBackupStatus)
  }
}
