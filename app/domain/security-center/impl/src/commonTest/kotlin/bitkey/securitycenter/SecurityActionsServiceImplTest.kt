package bitkey.securitycenter

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SecurityActionsServiceImplTest : FunSpec({
  test("getActions when category is recovery") {
    val actions = securityActionsService().getActions(SecurityActionCategory.RECOVERY)
    actions.size shouldBe 5
    actions.map { it.getRecommendations() }.flatten() shouldBe listOf(
      SecurityActionRecommendation.ENABLE_CRITICAL_ALERTS,
      SecurityActionRecommendation.ADD_TRUSTED_CONTACTS,
      SecurityActionRecommendation.BACKUP_MOBILE_KEY,
      SecurityActionRecommendation.ADD_BENEFICIARY,
      SecurityActionRecommendation.BACKUP_EAK
    )
  }

  test("getActions when category is security") {
    val actions = securityActionsService().getActions(SecurityActionCategory.SECURITY)
    actions.size shouldBe 2
    actions.map { it.getRecommendations() }.flatten() shouldBe listOf(
      SecurityActionRecommendation.ADD_FINGERPRINTS,
      SecurityActionRecommendation.SETUP_BIOMETRICS
    )
  }

  test("getRecommendations returns recommendations in the correct order") {
    val recommendedActions = securityActionsService().getRecommendations()

    recommendedActions shouldBe listOf(
      SecurityActionRecommendation.BACKUP_MOBILE_KEY,
      SecurityActionRecommendation.BACKUP_EAK,
      SecurityActionRecommendation.ADD_FINGERPRINTS,
      SecurityActionRecommendation.ADD_TRUSTED_CONTACTS,
      SecurityActionRecommendation.ENABLE_CRITICAL_ALERTS,
      SecurityActionRecommendation.ADD_BENEFICIARY,
      SecurityActionRecommendation.SETUP_BIOMETRICS
    )
  }
})

suspend fun securityActionsService(): SecurityActionsService {
  return SecurityActionsServiceImpl(
    MobileKeyCloudBackupHealthActionFactoryFake(),
    EakCloudBackupHealthActionFactoryFake(),
    SocialRecoveryActionFactoryFake(),
    InheritanceActionFactoryFake(),
    BiometricActionFactoryFake(),
    CriticalAlertsActionFactoryFake(),
    FingerprintsActionFactoryFake()
  )
}
