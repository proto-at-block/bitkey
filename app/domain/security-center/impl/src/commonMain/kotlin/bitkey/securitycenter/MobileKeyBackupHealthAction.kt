package bitkey.securitycenter

import build.wallet.account.AccountService
import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.cloud.backup.health.MobileKeyBackupStatus
import build.wallet.cloud.backup.health.isHealthy
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logError
import kotlinx.coroutines.flow.first

data class MobileKeyBackupHealthAction(
  private val cloudBackupStatus: MobileKeyBackupStatus,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> {
    return listOfNotNull(
      SecurityActionRecommendation.BACKUP_MOBILE_KEY.takeIf { !cloudBackupStatus.isHealthy() }
    )
  }

  override fun category(): SecurityActionCategory {
    return SecurityActionCategory.RECOVERY
  }
}

interface MobileKeyBackupHealthActionFactory {
  suspend fun create(): SecurityAction?
}

@BitkeyInject(AppScope::class)
class MobileKeyBackupHealthActionFactoryImpl(
  private val cloudBackupHealthRepository: CloudBackupHealthRepository,
  private val accountService: AccountService,
) : MobileKeyBackupHealthActionFactory {
  override suspend fun create(): SecurityAction? {
    val account = accountService.activeAccount().first()
    if (account !is FullAccount) {
      val message = "No active full account found when checking cloud backup. Found account: $account."
      logError { message }
      return null
    }
    val cloudBackupStatus = cloudBackupHealthRepository.performSync(account)
    return MobileKeyBackupHealthAction(cloudBackupStatus.mobileKeyBackupStatus)
  }
}
