package build.wallet.platform.biometrics

actual class BiometricTextProviderImpl : BiometricTextProvider {
  override fun getSettingsTitleText(): String = "Noop Biometrics"

  override fun getSettingsSecondaryText(): String = "Noop Biometrics Description"
}
