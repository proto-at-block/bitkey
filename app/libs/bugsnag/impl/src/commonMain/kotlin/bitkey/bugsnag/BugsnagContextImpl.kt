package bitkey.bugsnag

import build.wallet.account.analytics.AppInstallationDao
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@BitkeyInject(AppScope::class)
class BugsnagContextImpl(
  private val appCoroutineScope: CoroutineScope,
  private val appInstallationDao: AppInstallationDao,
) : BugsnagContext {
  override fun configureCommonMetadata() {
    appCoroutineScope.launch {
      // Add App Installation ID to Bugsnag metadata.
      appInstallationDao.getOrCreateAppInstallation()
        .onSuccess { appInstallation ->
          bugsnagSetCustomValue(
            section = "account",
            key = "app_installation_id",
            value = appInstallation.localId
          )
        }
        .logFailure { "Failed to get app installation for bugsnag metadata" }
    }
  }
}
