package bitkey.securitycenter

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.fwup.FirmwareDataService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface HardwareDeviceActionFactory {
  suspend fun create(): Flow<SecurityAction?>
}

@BitkeyInject(AppScope::class)
class HardwareDeviceActionFactoryImpl(
  private val firmwareDataService: FirmwareDataService,
) : HardwareDeviceActionFactory {
  override suspend fun create(): Flow<SecurityAction> {
    return firmwareDataService.firmwareData().map {
      HardwareDeviceAction(firmwareData = it)
    }
  }
}
