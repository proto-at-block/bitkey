package build.wallet.platform.biometrics

class BiometricTextProviderFake : BiometricTextProvider {
  override fun getSettingsTitleText() = "Biometrics"

  override fun getSettingsSecondaryText() = "Use Biometrics to unlock app"

  override fun getAppSecurityDescriptionText() =
    "Unlock the app using fingerprint or facial recognition."
}
