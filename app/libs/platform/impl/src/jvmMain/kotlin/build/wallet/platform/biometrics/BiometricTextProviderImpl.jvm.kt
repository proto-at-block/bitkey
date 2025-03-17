package build.wallet.platform.biometrics

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class BiometricTextProviderImpl : BiometricTextProvider {
  override fun getSettingsTitleText(): String = "Noop Biometrics"

  override fun getSettingsSecondaryText(): String = "Noop Biometrics Description"
}
