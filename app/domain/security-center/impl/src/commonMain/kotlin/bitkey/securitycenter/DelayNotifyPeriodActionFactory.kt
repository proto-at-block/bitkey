package bitkey.securitycenter

import bitkey.account.HardwareType
import build.wallet.account.AccountService
import build.wallet.bitkey.account.FullAccount
import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.ConfigurableDelayNotifyFeatureFlag
import build.wallet.feature.flags.ConfigurableDelayNotifyW3MinFirmwareVersionFeatureFlag
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.fwup.semverToInt
import build.wallet.logging.logWarn
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrElse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface DelayNotifyPeriodActionFactory {
  fun create(): Flow<SecurityAction?>
}

@BitkeyInject(AppScope::class)
class DelayNotifyPeriodActionFactoryImpl(
  private val accountService: AccountService,
  private val delayNotifyConfigurationService: DelayNotifyConfigurationService,
  private val configurableDelayNotifyFeatureFlag: ConfigurableDelayNotifyFeatureFlag,
  private val configurableDelayNotifyW3MinFirmwareVersionFeatureFlag:
    ConfigurableDelayNotifyW3MinFirmwareVersionFeatureFlag,
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
) : DelayNotifyPeriodActionFactory {
  override fun create(): Flow<SecurityAction?> {
    return combine(
      configurableDelayNotifyFeatureFlag.flagValue(),
      accountService.activeAccount()
    ) { flag, account ->
      (account as? FullAccount)?.takeIf { flag.value }
    }.flatMapLatest { account ->
      when {
        account == null -> flowOf(null)
        // W1 is not gated by firmware version.
        account.config.hardwareType != HardwareType.W3 -> periodAction(account)
        else ->
          combine(
            configurableDelayNotifyW3MinFirmwareVersionFeatureFlag.flagValue(),
            firmwareDeviceInfoDao.deviceInfo()
          ) { minVersionFlag, deviceInfoResult ->
            isSupportedW3Firmware(
              firmwareDeviceInfo = deviceInfoResult.get(),
              minFirmwareVersion = minVersionFlag.value
            )
          }.flatMapLatest { supported ->
            if (supported) periodAction(account) else flowOf(null)
          }
      }
    }
  }

  private fun periodAction(account: FullAccount): Flow<SecurityAction?> {
    return delayNotifyConfigurationService.delayNotifyPeriod(account).map { periodDays ->
      periodDays?.let {
        DelayNotifyPeriodAction(currentPeriodDays = it)
      }
    }
  }

  /**
   * W3 firmware must support the SetDelayNotifyPeriod privileged action proof; on older
   * firmware the hardware confirmation fails and the customer gets stuck in a retry loop.
   * Hide the action on W3 unless the paired device's firmware version meets the
   * flag-provided minimum. Fails closed: missing device info or a malformed flag value
   * hides the action rather than risking the unsupported flow.
   */
  private fun isSupportedW3Firmware(
    firmwareDeviceInfo: FirmwareDeviceInfo?,
    minFirmwareVersion: String,
  ): Boolean {
    val minVersion = minFirmwareVersion.trim()
    if (minVersion.isEmpty()) return false
    // Reject cached metadata from previously paired non-W3 hardware (e.g. right after
    // a W1 -> W3 upgrade); a stale W1 version must not satisfy the W3 minimum.
    if (firmwareDeviceInfo?.hardwareType() != HardwareType.W3) return false
    val firmwareVersion = firmwareDeviceInfo.version
    return catchingResult {
      semverToInt(firmwareVersion) >= semverToInt(minVersion)
    }.getOrElse { error ->
      logWarn(throwable = error) {
        "Ignoring invalid configurable D&N W3 min firmware version flag: " +
          "'$minVersion' (device firmware: '$firmwareVersion')"
      }
      false
    }
  }
}
