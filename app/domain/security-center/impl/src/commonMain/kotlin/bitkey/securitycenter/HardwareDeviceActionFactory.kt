package bitkey.securitycenter

import bitkey.account.isW3Hardware
import build.wallet.account.AccountService
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.fwup.FirmwareDataService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface HardwareDeviceActionFactory {
  fun create(): Flow<SecurityAction?>
}

@BitkeyInject(AppScope::class)
class HardwareDeviceActionFactoryImpl(
  private val firmwareDataService: FirmwareDataService,
  private val accountService: AccountService,
) : HardwareDeviceActionFactory {
  override fun create(): Flow<SecurityAction> {
    return combine(
      firmwareDataService.firmwareData(),
      accountService.activeAccount()
    ) { firmwareData, account ->
      val isHardwareDescriptorLoaded = account !is FullAccount ||
        !account.config.isW3Hardware ||
        !account.keybox.appGlobalAuthKeyHwSignature.isW3OnboardingPlaceholder

      HardwareDeviceAction(
        firmwareData = firmwareData,
        isHardwareDescriptorLoaded = isHardwareDescriptorLoaded
      )
    }
  }
}
