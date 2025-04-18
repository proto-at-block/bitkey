package bitkey.ui.screens.securityhub

import bitkey.securitycenter.SecurityActionRecommendation
import bitkey.securitycenter.SecurityActionRecommendation.*
import bitkey.securitycenter.SecurityActionsServiceFake
import bitkey.ui.framework.test
import build.wallet.navigation.v1.NavigationScreenId
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.ui.awaitBody
import build.wallet.time.MinimumLoadingDuration
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class SecurityHubPresenterTests : FunSpec({
  val securityActionsService = SecurityActionsServiceFake()

  val presenter = SecurityHubPresenter(
    securityActionsService = securityActionsService,
    minimumLoadingDuration = MinimumLoadingDuration(0.seconds)
  )

  beforeTest {
    securityActionsService.clear()
    Router.route = null
  }

  test("clicking a recommendation navigates to the correct route") {
    presenter.test(SecurityHubScreen(null, tabs = emptyList())) {
      // loading security actions
      awaitBody<SecurityHubBodyModel>()

      awaitBody<SecurityHubBodyModel> {
        onRecommendationClick(BACKUP_EAK)
      }

      Router.route.shouldBe(Route.NavigationDeeplink(NavigationScreenId.NAVIGATION_SCREEN_ID_EAK_BACKUP_HEALTH))
    }
  }

  test("recommendation maps to the correct navigation id") {
    SecurityActionRecommendation.entries.forEach { recommendation ->
      when (recommendation) {
        BACKUP_MOBILE_KEY -> recommendation.navigationScreenId()
          .shouldBe(NavigationScreenId.NAVIGATION_SCREEN_ID_MOBILE_KEY_BACKUP)
        BACKUP_EAK -> recommendation.navigationScreenId()
          .shouldBe(NavigationScreenId.NAVIGATION_SCREEN_ID_EAK_BACKUP_HEALTH)
        ADD_FINGERPRINTS -> recommendation.navigationScreenId()
          .shouldBe(NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_FINGERPRINTS)
        ADD_TRUSTED_CONTACTS -> recommendation.navigationScreenId()
          .shouldBe(NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_RECOVERY_CONTACTS)
        ENABLE_CRITICAL_ALERTS -> recommendation.navigationScreenId()
          .shouldBe(NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_CRITICAL_ALERTS)
        ADD_BENEFICIARY -> recommendation.navigationScreenId()
          .shouldBe(NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_INHERITANCE)
        SETUP_BIOMETRICS -> recommendation.navigationScreenId()
          .shouldBe(NavigationScreenId.NAVIGATION_SCREEN_ID_MANAGE_BIOMETRIC)
      }
    }
  }
})
