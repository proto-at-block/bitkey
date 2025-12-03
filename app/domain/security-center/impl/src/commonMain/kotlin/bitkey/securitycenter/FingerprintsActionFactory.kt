package bitkey.securitycenter

import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.firmware.HardwareUnlockInfoService
import bitkey.privilegedactions.FingerprintResetService
import bitkey.privilegedactions.FingerprintResetState
import bitkey.privilegedactions.isDelayAndNotifyReadyToComplete
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.feature.FeatureFlagValue.StringFlag
import build.wallet.feature.flags.FingerprintResetFeatureFlag
import build.wallet.feature.flags.FingerprintResetMinFirmwareVersionFeatureFlag
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.firmware.UnlockMethod
import build.wallet.fwup.semverToInt
import build.wallet.nfc.HardwareProvisionedAppKeyStatusDao
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlinx.datetime.Clock

interface FingerprintsActionFactory {
  suspend fun create(): Flow<SecurityAction?>
}

@BitkeyInject(AppScope::class)
class FingerprintsActionFactoryImpl(
  private val hardwareUnlockInfoService: HardwareUnlockInfoService,
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
  private val fingerprintResetService: FingerprintResetService,
  private val hardwareProvisionedAppKeyStatusDao: HardwareProvisionedAppKeyStatusDao,
  private val fingerprintResetMinFirmwareVersionFeatureFlag:
    FingerprintResetMinFirmwareVersionFeatureFlag,
  private val fingerprintResetFeatureFlag: FingerprintResetFeatureFlag,
  private val clock: Clock,
) : FingerprintsActionFactory {
  override suspend fun create(): Flow<SecurityAction> {
    val fingerprintResetReadyFlow = createFingerprintResetReadyFlow()

    return combine(
      hardwareUnlockInfoService
        .countUnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS),
      fingerprintResetReadyFlow,
      firmwareDeviceInfoDao.deviceInfo(),
      hardwareProvisionedAppKeyStatusDao.isKeyProvisionedForActiveAccountFlow(),
      fingerprintResetFeatureFlag.flagValue(),
      fingerprintResetMinFirmwareVersionFeatureFlag.flagValue()
    ) { values ->
      val count = values[0] as Int
      val resetReady = values[1] as Boolean
      val firmwareDeviceInfoResult = values[2] as Result<*, *>
      val firmwareDeviceInfo = firmwareDeviceInfoResult.get() as FirmwareDeviceInfo?
      val isAppKeyProvisioned = values[3] as Boolean
      val fingerprintResetFlagValue = values[4] as BooleanFlag
      val minFirmwareVersionFlagValue = values[5] as StringFlag

      val firmwareVersion = firmwareDeviceInfo?.version
      val minFirmwareVersion = minFirmwareVersionFlagValue.value
      val isFirmwareVersionSupported = if (firmwareVersion != null && minFirmwareVersion.isNotEmpty()) {
        semverToInt(firmwareVersion) >= semverToInt(minFirmwareVersion)
      } else {
        false
      }
      val isFingerprintResetEnabled = fingerprintResetFlagValue.value && isFirmwareVersionSupported

      FingerprintsAction(
        fingerprintCount = count,
        firmwareDeviceInfo = firmwareDeviceInfo,
        fingerprintResetReady = resetReady,
        isAppKeyProvisioned = isAppKeyProvisioned,
        isFingerprintResetEnabled = isFingerprintResetEnabled
      )
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
