package build.wallet.platform.biometrics

expect class BiometricTextProviderImpl() : BiometricTextProvider {
  override fun getSettingsTitleText(): String

  override fun getSettingsSecondaryText(): String
}
