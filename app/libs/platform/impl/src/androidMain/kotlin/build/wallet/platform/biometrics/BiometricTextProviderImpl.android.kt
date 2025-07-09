package build.wallet.platform.biometrics

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class BiometricTextProviderImpl : BiometricTextProvider {
  override fun getSettingsTitleText() = "Biometrics"

  override fun getSettingsSecondaryText(): String = "Use Biometrics to unlock app"

  override fun getAppSecurityDescriptionText(): String =
    "Unlock the app using fingerprint or facial recognition."
}
