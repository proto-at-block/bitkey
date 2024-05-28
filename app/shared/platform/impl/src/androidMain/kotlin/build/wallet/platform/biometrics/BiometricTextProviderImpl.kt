package build.wallet.platform.biometrics

actual class BiometricTextProviderImpl : BiometricTextProvider {
  override fun getSettingsTitleText() = "Biometrics"

  override fun getSettingsSecondaryText(): String = "Use Biometrics to unlock app"
}
