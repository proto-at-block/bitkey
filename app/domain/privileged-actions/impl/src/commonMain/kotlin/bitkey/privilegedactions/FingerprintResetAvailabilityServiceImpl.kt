package bitkey.privilegedactions

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.FingerprintResetFeatureFlag
import build.wallet.feature.flags.FingerprintResetMinFirmwareVersionFeatureFlag
import build.wallet.fwup.FirmwareDataService
import build.wallet.fwup.semverToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@BitkeyInject(AppScope::class)
class FingerprintResetAvailabilityServiceImpl(
  private val fingerprintResetFeatureFlag: FingerprintResetFeatureFlag,
  private val fingerprintResetMinFirmwareVersionFeatureFlag:
    FingerprintResetMinFirmwareVersionFeatureFlag,
  private val firmwareDataService: FirmwareDataService,
) : FingerprintResetAvailabilityService {
  override fun isAvailable(): Flow<Boolean> {
    return combine(
      fingerprintResetFeatureFlag.flagValue(),
      fingerprintResetMinFirmwareVersionFeatureFlag.flagValue(),
      firmwareDataService.firmwareData()
    ) { isFlagEnabled, minVersionFlag, firmwareData ->
      val firmwareVersion = firmwareData.firmwareDeviceInfo?.version
      val minFirmwareVersion = minVersionFlag.value
      val isVersionSupported = if (firmwareVersion != null && minFirmwareVersion.isNotEmpty()) {
        semverToInt(firmwareVersion) >= semverToInt(minFirmwareVersion)
      } else {
        false
      }
      isFlagEnabled.value && isVersionSupported
    }
  }
}
