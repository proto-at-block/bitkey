package bitkey.securitycenter

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

  override fun type(): SecurityActionType = SecurityActionType.BIOMETRIC

  override fun state(): SecurityActionState {
    return if (biometricsEnabled) {
      SecurityActionState.Secure
    } else {
      SecurityActionState.HasRecommendationActions
    }
  }
}
