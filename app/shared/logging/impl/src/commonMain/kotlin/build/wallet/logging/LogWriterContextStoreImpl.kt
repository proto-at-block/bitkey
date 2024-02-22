package build.wallet.logging

import build.wallet.account.analytics.AppInstallationDao
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class LogWriterContextStoreImpl(
  private val appInstallationDao: AppInstallationDao,
) : LogWriterContextStore {
  private val context = MutableStateFlow(LogWriterContext())

  override fun get(): LogWriterContext {
    return context.value
  }

  override suspend fun syncContext() {
    appInstallationDao.getOrCreateAppInstallation()
      .onSuccess { appInstallation ->
        // atomically update context
        context.update {
          it.copy(
            appInstallationId = appInstallation.localId,
            hardwareSerialNumber = appInstallation.hardwareSerialNumber
          )
        }
      }
  }
}
