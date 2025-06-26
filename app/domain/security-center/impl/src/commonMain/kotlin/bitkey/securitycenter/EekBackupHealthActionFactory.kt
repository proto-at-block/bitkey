package bitkey.securitycenter

import build.wallet.account.AccountService
import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logError
import kotlinx.coroutines.flow.*

interface EekBackupHealthActionFactory {
  suspend fun create(): Flow<SecurityAction?>
}

@BitkeyInject(AppScope::class)
class EekBackupHealthActionFactoryImpl(
  private val cloudBackupHealthRepository: CloudBackupHealthRepository,
  private val accountService: AccountService,
) : EekBackupHealthActionFactory {
  override suspend fun create(): Flow<SecurityAction?> {
    val account = accountService.activeAccount().first()
    if (account !is FullAccount) {
      val message = "No active full account found when checking cloud backup. Found account: $account."
      logError { message }
      return flowOf(null)
    }
    return cloudBackupHealthRepository.eekBackupStatus()
      .map { status -> status?.let(::EekBackupHealthAction) }
  }
}
