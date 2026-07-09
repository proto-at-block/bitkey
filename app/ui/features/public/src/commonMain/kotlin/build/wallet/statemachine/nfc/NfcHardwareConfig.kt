package build.wallet.statemachine.nfc

import bitkey.account.AccountConfig
import bitkey.account.DefaultAccountConfig
import bitkey.account.FullAccountConfig
import bitkey.account.LiteAccountConfig
import bitkey.account.SoftwareAccountConfig
import build.wallet.platform.config.AppVariant
import build.wallet.statemachine.settings.showDebugMenu

internal fun isHardwareFakeForNfc(
  appVariant: AppVariant,
  accountConfig: AccountConfig,
  defaultConfig: DefaultAccountConfig,
): Boolean {
  return when (accountConfig) {
    is FullAccountConfig, is DefaultAccountConfig -> isAccountHardwareFakeForNfc(accountConfig)
    is LiteAccountConfig -> appVariant.showDebugMenu && defaultConfig.isHardwareFake
    is SoftwareAccountConfig -> false
  }
}

internal fun isAccountHardwareFakeForNfc(accountConfig: AccountConfig): Boolean {
  return when (accountConfig) {
    is FullAccountConfig -> accountConfig.isHardwareFake
    is DefaultAccountConfig -> accountConfig.isHardwareFake
    is LiteAccountConfig -> false
    is SoftwareAccountConfig -> false
  }
}
