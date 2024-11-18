package build.wallet.platform.biometrics

actual class BiometricTextProviderImpl : BiometricTextProvider {
  actual override fun getSettingsTitleText() = "Biometrics"

  actual override fun getSettingsSecondaryText(): String = "Use Biometrics to unlock app"
}
