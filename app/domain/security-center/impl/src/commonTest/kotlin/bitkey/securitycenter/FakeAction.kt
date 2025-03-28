package bitkey.securitycenter

data class FakeAction(
  private val recommendations: List<SecurityActionRecommendation>,
  private val category: SecurityActionCategory,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> {
    return recommendations
  }

  override fun category(): SecurityActionCategory {
    return category
  }
}

class MobileKeyCloudBackupHealthActionFactoryFake : MobileKeyBackupHealthActionFactory {
  override suspend fun create(): SecurityAction {
    return FakeAction(
      recommendations = listOf(SecurityActionRecommendation.BACKUP_MOBILE_KEY),
      category = SecurityActionCategory.RECOVERY
    )
  }
}

class EakCloudBackupHealthActionFactoryFake : EakBackupHealthActionFactory {
  override suspend fun create(): SecurityAction {
    return FakeAction(
      recommendations = listOf(SecurityActionRecommendation.BACKUP_EAK),
      category = SecurityActionCategory.RECOVERY
    )
  }
}

class SocialRecoveryActionFactoryFake : SocialRecoveryActionFactory {
  override suspend fun create(): SecurityAction {
    return FakeAction(
      recommendations = listOf(SecurityActionRecommendation.ADD_TRUSTED_CONTACTS),
      category = SecurityActionCategory.RECOVERY
    )
  }
}

class InheritanceActionFactoryFake : InheritanceActionFactory {
  override suspend fun create(): SecurityAction {
    return FakeAction(
      recommendations = listOf(SecurityActionRecommendation.ADD_BENEFICIARY),
      category = SecurityActionCategory.RECOVERY
    )
  }
}

class BiometricActionFactoryFake : BiometricActionFactory {
  override suspend fun create(): SecurityAction {
    return FakeAction(
      recommendations = listOf(SecurityActionRecommendation.SETUP_BIOMETRICS),
      category = SecurityActionCategory.SECURITY
    )
  }
}

class CriticalAlertsActionFactoryFake : CriticalAlertsActionFactory {
  override suspend fun create(): SecurityAction {
    return FakeAction(
      recommendations = listOf(SecurityActionRecommendation.ENABLE_CRITICAL_ALERTS),
      category = SecurityActionCategory.RECOVERY
    )
  }
}

class FingerprintsActionFactoryFake : FingerprintsActionFactory {
  override suspend fun create(): SecurityAction {
    return FakeAction(
      recommendations = listOf(SecurityActionRecommendation.ADD_FINGERPRINTS),
      category = SecurityActionCategory.SECURITY
    )
  }
}
