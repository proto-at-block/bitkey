package build.wallet.platform.settings

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logDebug

@BitkeyInject(AppScope::class)
class SystemSettingsLauncherImpl : SystemSettingsLauncher {
  override fun launchAppSettings() {
    logDebug { "Launch App Settings" }
  }

  override fun launchSecuritySettings() {
    logDebug { "Launch Security Settings" }
  }
}
