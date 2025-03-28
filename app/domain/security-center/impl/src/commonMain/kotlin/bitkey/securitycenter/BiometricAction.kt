package bitkey.securitycenter

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.inappsecurity.BiometricAuthService

class BiometricAction(
  private val biometricsEnabled: Boolean,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> =
    if (biometricsEnabled) {
      emptyList()
    } else {
      listOf(SecurityActionRecommendation.SETUP_BIOMETRICS)
    }

  override fun category(): SecurityActionCategory = SecurityActionCategory.SECURITY
}

interface BiometricActionFactory {
  suspend fun create(): SecurityAction?
}

@BitkeyInject(AppScope::class)
class BiometricActionFactoryImpl(
  private val biometricService: BiometricAuthService,
) : BiometricActionFactory {
  override suspend fun create(): SecurityAction =
    BiometricAction(biometricService.isBiometricAuthRequired().value)
}
