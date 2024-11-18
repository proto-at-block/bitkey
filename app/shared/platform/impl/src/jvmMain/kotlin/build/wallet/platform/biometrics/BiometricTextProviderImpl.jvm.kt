package build.wallet.platform.biometrics

actual class BiometricTextProviderImpl : BiometricTextProvider {
  actual override fun getSettingsTitleText(): String = "Noop Biometrics"

  actual override fun getSettingsSecondaryText(): String = "Noop Biometrics Description"
}
