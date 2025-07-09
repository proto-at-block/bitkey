package bitkey.securitycenter

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class BiometricActionTest : FunSpec({

  test("Test BiometricAction with biometrics enabled") {
    val action = BiometricAction(biometricsEnabled = true)
    action.getRecommendations().shouldBeEmpty()
    action.category() shouldBe SecurityActionCategory.SECURITY
    action.type() shouldBe SecurityActionType.BIOMETRIC
    action.state() shouldBe SecurityActionState.Secure
  }

  test("Test BiometricAction with biometrics disabled") {
    val action = BiometricAction(biometricsEnabled = false)
    action.getRecommendations() shouldBe listOf(SecurityActionRecommendation.SETUP_BIOMETRICS)
    action.category() shouldBe SecurityActionCategory.SECURITY
    action.type() shouldBe SecurityActionType.BIOMETRIC
    action.state() shouldBe SecurityActionState.HasRecommendationActions
  }
})
