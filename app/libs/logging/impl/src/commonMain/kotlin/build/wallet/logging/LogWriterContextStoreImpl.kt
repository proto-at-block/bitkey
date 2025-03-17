package build.wallet.logging

import build.wallet.account.analytics.AppInstallationDao
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.FirmwareDeviceInfoDao
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

@BitkeyInject(AppScope::class)
class LogWriterContextStoreImpl(
  private val appInstallationDao: AppInstallationDao,
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
) : LogWriterContextStore {
  private val context = MutableStateFlow(LogWriterContext())

  override fun get(): LogWriterContext = context.value

  override suspend fun syncContext() {
    appInstallationDao
      .getOrCreateAppInstallation()
      .onSuccess { appInstallation ->
        // atomically update context
        context.update {
          it.copy(
            appInstallationId = appInstallation.localId,
            hardwareSerialNumber = appInstallation.hardwareSerialNumber
          )
        }
      }

    firmwareDeviceInfoDao
      .getDeviceInfo()
      .onSuccess { firmwareDeviceInfo ->
        context.update {
          it.copy(firmwareVersion = firmwareDeviceInfo?.version)
        }
      }
  }
}
