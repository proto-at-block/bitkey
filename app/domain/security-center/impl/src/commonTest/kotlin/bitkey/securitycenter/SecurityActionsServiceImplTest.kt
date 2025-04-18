package bitkey.securitycenter

import app.cash.turbine.test
import bitkey.metrics.MetricOutcome
import bitkey.metrics.MetricTrackerService
import bitkey.metrics.MetricTrackerServiceFake
import bitkey.metrics.TrackedMetric
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action
import build.wallet.coroutines.turbine.turbines
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class SecurityActionsServiceImplTest : FunSpec({
  val eventTracker = EventTrackerMock(turbines::create)
  val metricTrackerService = MetricTrackerServiceFake()
  val service = securityActionsService(eventTracker, metricTrackerService)

  beforeTest {
    metricTrackerService.reset()
  }

  test("getActions when category is recovery") {
    val actions = service.getActions(SecurityActionCategory.RECOVERY)
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
    val actions = service.getActions(SecurityActionCategory.SECURITY)
    actions.size shouldBe 2
    actions.map { it.getRecommendations() }.flatten() shouldBe listOf(
      SecurityActionRecommendation.ADD_FINGERPRINTS,
      SecurityActionRecommendation.SETUP_BIOMETRICS
    )
  }

  test("getRecommendations returns recommendations in the correct order") {
    val recommendedActions = service.getRecommendations()

    recommendedActions.test {
      awaitItem() shouldBe listOf(
        SecurityActionRecommendation.BACKUP_MOBILE_KEY,
        SecurityActionRecommendation.BACKUP_EAK,
        SecurityActionRecommendation.ADD_FINGERPRINTS,
        SecurityActionRecommendation.ADD_TRUSTED_CONTACTS,
        SecurityActionRecommendation.ENABLE_CRITICAL_ALERTS,
        SecurityActionRecommendation.ADD_BENEFICIARY,
        SecurityActionRecommendation.SETUP_BIOMETRICS
      )

      eventTracker.eventCalls.skipItems(7)
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("getRecommendations tracks the correct events") {
    val fingerprintFactory = FingerprintsActionFactoryFake()
    fingerprintFactory.includeRecommendations = false

    val service = SecurityActionsServiceImpl(
      MobileKeyCloudBackupHealthActionFactoryFake(),
      EakCloudBackupHealthActionFactoryFake(),
      SocialRecoveryActionFactoryFake(),
      InheritanceActionFactoryFake(),
      BiometricActionFactoryFake(),
      CriticalAlertsActionFactoryFake(),
      fingerprintFactory,
      eventTracker,
      metricTrackerService
    )

    service.getRecommendations().test {
      awaitItem()
      SecurityActionRecommendation.entries.forEach { recommendation ->
        val isPending = when (recommendation) {
          SecurityActionRecommendation.ADD_FINGERPRINTS -> false
          else -> true
        }

        eventTracker.eventCalls.awaitItem() shouldBe TrackedAction(
          action = Action.ACTION_APP_SECURITY_CENTER_CHECK,
          screenId = null,
          context = SecurityCenterScreenIdContext(recommendation, isPending = isPending)
        )
      }

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("metrics are tracked correctly") {
    val recommendedActions = service.getRecommendations()

    recommendedActions.test {
      metricTrackerService.completedMetrics.shouldContainExactlyInAnyOrder(
        SecurityActionType.entries.map { actionType ->
          MetricTrackerServiceFake.CompletedMetric(
            metric = TrackedMetric(
              name = SecurityActionMetricDefinition(actionType).name,
              variant = null
            ),
            outcome = MetricOutcome.Succeeded
          )
        }
      )
      eventTracker.eventCalls.skipItems(7)
      cancelAndIgnoreRemainingEvents()
    }
  }
})

fun securityActionsService(
  eventTracker: EventTracker,
  metricTrackerService: MetricTrackerService,
): SecurityActionsService {
  return SecurityActionsServiceImpl(
    MobileKeyCloudBackupHealthActionFactoryFake(),
    EakCloudBackupHealthActionFactoryFake(),
    SocialRecoveryActionFactoryFake(),
    InheritanceActionFactoryFake(),
    BiometricActionFactoryFake(),
    CriticalAlertsActionFactoryFake(),
    FingerprintsActionFactoryFake(),
    eventTracker,
    metricTrackerService
  )
}
