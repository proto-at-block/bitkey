package build.wallet.logging

import build.wallet.account.analytics.AppInstallation
import build.wallet.account.analytics.AppInstallationDao
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.platform.app.AppSessionManager
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@BitkeyInject(AppScope::class)
class LogWriterContextStoreImpl(
  private val appInstallationDao: AppInstallationDao,
  private val appSessionManager: AppSessionManager,
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
  private val appCoroutineScope: CoroutineScope,
) : LogWriterContextStore {
  private val context = MutableStateFlow(LogWriterContext())

  override fun get(): LogWriterContext =
    context.value.copy(
      // Fetched dynamically (not in syncContext) because session ID can change when the app
      // returns from being backgrounded for >5 minutes.
      appSessionId = appSessionManager.getSessionId()
    )

  override suspend fun syncContext() {
    // Initial one-shot sync so context is populated before Logger.configure() runs.
    appInstallationDao.getOrCreateAppInstallation().onSuccess { applyAppInstallation(it) }
    firmwareDeviceInfoDao.getDeviceInfo().onSuccess { applyFirmwareDeviceInfo(it) }

    // Launch background observers so context stays current when values change after startup
    // (e.g. hardware serial updated during pairing or cloud backup restoration).
    appCoroutineScope.launch {
      appInstallationDao.appInstallation().collect { result ->
        result.onSuccess { applyAppInstallation(it) }
      }
    }
    appCoroutineScope.launch {
      firmwareDeviceInfoDao.deviceInfo().collect { result ->
        result.onSuccess { applyFirmwareDeviceInfo(it) }
      }
    }
  }

  private fun applyAppInstallation(appInstallation: AppInstallation?) {
    context.update {
      it.copy(
        appInstallationId = appInstallation?.localId,
        hardwareSerialNumber = appInstallation?.hardwareSerialNumber
      )
    }
  }

  private fun applyFirmwareDeviceInfo(firmwareDeviceInfo: FirmwareDeviceInfo?) {
    context.update {
      it.copy(firmwareVersion = firmwareDeviceInfo?.version)
    }
  }
}
