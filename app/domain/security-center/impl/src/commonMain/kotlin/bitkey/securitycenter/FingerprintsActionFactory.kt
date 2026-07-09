package bitkey.securitycenter

import bitkey.account.HardwareType
import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.firmware.HardwareUnlockInfoService
import bitkey.privilegedactions.FingerprintResetService
import bitkey.privilegedactions.FingerprintResetState
import bitkey.privilegedactions.isDelayAndNotifyReadyToComplete
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.FingerprintResetMinFirmwareVersionFeatureFlag
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.firmware.UnlockMethod
import build.wallet.fwup.semverToInt
import build.wallet.nfc.HardwareProvisionedAppKeyStatusDao
import com.github.michaelbull.result.get
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformLatest
import kotlinx.datetime.Clock

interface FingerprintsActionFactory {
  fun create(): Flow<SecurityAction?>
}

@BitkeyInject(AppScope::class)
class FingerprintsActionFactoryImpl(
  private val hardwareUnlockInfoService: HardwareUnlockInfoService,
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
  private val fingerprintResetService: FingerprintResetService,
  private val hardwareProvisionedAppKeyStatusDao: HardwareProvisionedAppKeyStatusDao,
  private val fingerprintResetMinFirmwareVersionFeatureFlag:
    FingerprintResetMinFirmwareVersionFeatureFlag,
  private val clock: Clock,
) : FingerprintsActionFactory {
  override fun create(): Flow<SecurityAction?> {
    return firmwareDeviceInfoDao.deviceInfo().flatMapLatest { firmwareDeviceInfoResult ->
      val firmwareDeviceInfo = firmwareDeviceInfoResult.get() as FirmwareDeviceInfo?

      // W3 hardware does not support fingerprint management, so hide this tile entirely.
      if (firmwareDeviceInfo?.hardwareType() == HardwareType.W3) {
        flowOf(null)
      } else {
        combine(
          hardwareUnlockInfoService
            .countUnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS),
          createFingerprintResetReadyFlow(),
          hardwareProvisionedAppKeyStatusDao.isKeyProvisionedForActiveAccountFlow(),
          fingerprintResetMinFirmwareVersionFeatureFlag.flagValue()
        ) { count, resetReady, isAppKeyProvisioned, minFirmwareVersionFlagValue ->
          val firmwareVersion = firmwareDeviceInfo?.version
          val minFirmwareVersion = minFirmwareVersionFlagValue.value
          val isFingerprintResetEnabled = if (firmwareVersion != null && minFirmwareVersion.isNotEmpty()) {
            semverToInt(firmwareVersion) >= semverToInt(minFirmwareVersion)
          } else {
            false
          }

          FingerprintsAction(
            fingerprintCount = count,
            firmwareDeviceInfo = firmwareDeviceInfo,
            fingerprintResetReady = resetReady,
            isAppKeyProvisioned = isAppKeyProvisioned,
            isFingerprintResetEnabled = isFingerprintResetEnabled
          )
        }
      }
    }
  }

  private fun createFingerprintResetReadyFlow(): Flow<Boolean> {
    return fingerprintResetService
      .fingerprintResetAction
      .combine(fingerprintResetService.pendingFingerprintResetGrant()) { actionInstance, grant ->
        when {
          // Persisted grant is ready to complete immediately
          grant != null -> FingerprintResetState.GrantReady(grant)

          // Check server-side action
          else -> {
            val delayAndNotify = actionInstance?.authorizationStrategy as? AuthorizationStrategy.DelayAndNotify
            when {
              actionInstance == null || delayAndNotify == null -> FingerprintResetState.None
              actionInstance.isDelayAndNotifyReadyToComplete(clock) -> FingerprintResetState.DelayCompleted(actionInstance)
              else -> FingerprintResetState.DelayInProgress(actionInstance, delayAndNotify)
            }
          }
        }
      }
      .distinctUntilChanged()
      .transformLatest { resetState: FingerprintResetState ->
        when (resetState) {
          is FingerprintResetState.GrantReady,
          is FingerprintResetState.DelayCompleted,
          -> {
            emit(true)
          }

          is FingerprintResetState.DelayInProgress -> {
            emit(false)
            val remaining = resetState.delayAndNotify.delayEndTime - clock.now()
            if (remaining.isPositive()) {
              delay(remaining.inWholeMilliseconds)
            }
            emit(true)
          }

          is FingerprintResetState.None -> {
            emit(false)
          }
        }
      }
  }
}
