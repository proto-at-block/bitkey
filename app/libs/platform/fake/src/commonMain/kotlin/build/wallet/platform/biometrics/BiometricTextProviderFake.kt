package build.wallet.platform.biometrics

class BiometricTextProviderFake : BiometricTextProvider {
  override fun getSettingsTitleText() = "Biometrics"

  override fun getSettingsSecondaryText() = "Use Biometrics to unlock app"
}
