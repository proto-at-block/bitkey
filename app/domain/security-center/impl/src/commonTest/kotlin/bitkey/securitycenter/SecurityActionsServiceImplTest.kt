package bitkey.securitycenter

import app.cash.turbine.test
import bitkey.metrics.MetricOutcome
import bitkey.metrics.MetricTrackerServiceFake
import bitkey.metrics.TrackedMetric
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action
import build.wallet.coroutines.turbine.turbines
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock

class SecurityActionsServiceImplTest : FunSpec({
  val eventTracker = EventTrackerMock(turbines::create)
  val metricTrackerService = MetricTrackerServiceFake()
  val securityRecommendationInteractionDao = SecurityRecommendationInteractionDaoMock()
  val clock = Clock.System

  val service = SecurityActionsServiceImpl(
    AppKeyCloudBackupHealthActionFactoryFake(),
    EekCloudBackupHealthActionFactoryFake(),
    SocialRecoveryActionFactoryFake(),
    BiometricActionFactoryFake(),
    CriticalAlertsActionFactoryFake(),
    FingerprintsActionFactoryFake(),
    HardwareDeviceActionFactoryFake(),
    TxVerificationActionFactoryFake(),
    eventTracker,
    metricTrackerService,
    securityRecommendationInteractionDao,
    clock
  )

  beforeTest {
    metricTrackerService.reset()
  }

  test("getActions when category is recovery") {
    val actions = service.getActions(SecurityActionCategory.RECOVERY)
    actions.size shouldBe 4
    actions.map { it.type() } shouldBe listOf(
      SecurityActionType.CRITICAL_ALERTS,
      SecurityActionType.SOCIAL_RECOVERY,
      SecurityActionType.APP_KEY_BACKUP,
      SecurityActionType.EEK_BACKUP
    )
  }

  test("getActions when category is security") {
    val actions = service.getActions(SecurityActionCategory.SECURITY)
    actions.size shouldBe 4
    actions.map { it.type() } shouldBe listOf(
      SecurityActionType.HARDWARE_DEVICE,
      SecurityActionType.FINGERPRINTS,
      SecurityActionType.BIOMETRIC,
      SecurityActionType.TRANSACTION_VERIFICATION
    )
  }

  test("getRecommendations returns recommendations in the correct order") {
    val recommendedActions = service.getRecommendations()

    recommendedActions.test {
      awaitItem() shouldBe listOf(
        SecurityActionRecommendation.PAIR_HARDWARE_DEVICE,
        SecurityActionRecommendation.BACKUP_MOBILE_KEY,
        SecurityActionRecommendation.BACKUP_EAK,
        SecurityActionRecommendation.ADD_FINGERPRINTS,
        SecurityActionRecommendation.ADD_TRUSTED_CONTACTS,
        SecurityActionRecommendation.UPDATE_FIRMWARE,
        SecurityActionRecommendation.ENABLE_CRITICAL_ALERTS,
        SecurityActionRecommendation.SETUP_BIOMETRICS,
        SecurityActionRecommendation.ENABLE_TRANSACTION_VERIFICATION
      )

      eventTracker.eventCalls.skipItems(12)
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("getRecommendations tracks the correct events") {
    val fingerprintFactory = FingerprintsActionFactoryFake()
    fingerprintFactory.includeRecommendations = false

    val service = SecurityActionsServiceImpl(
      AppKeyCloudBackupHealthActionFactoryFake(),
      EekCloudBackupHealthActionFactoryFake(),
      SocialRecoveryActionFactoryFake(),
      // InheritanceActionFactoryFake(),
      BiometricActionFactoryFake(),
      CriticalAlertsActionFactoryFake(),
      fingerprintFactory,
      HardwareDeviceActionFactoryFake(),
      TxVerificationActionFactoryFake(),
      eventTracker,
      metricTrackerService,
      securityRecommendationInteractionDao,
      clock
    )

    service.getRecommendations().test {
      awaitItem()
      SecurityActionRecommendation.entries.forEach { recommendation ->
        if (recommendation == SecurityActionRecommendation.ADD_BENEFICIARY) {
          return@forEach
        }
        val isPending = when (recommendation) {
          SecurityActionRecommendation.ADD_FINGERPRINTS -> false
          SecurityActionRecommendation.ENABLE_PUSH_NOTIFICATIONS -> false
          SecurityActionRecommendation.ENABLE_EMAIL_NOTIFICATIONS -> false
          SecurityActionRecommendation.ENABLE_SMS_NOTIFICATIONS -> false
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
        SecurityActionType.entries.mapNotNull { actionType ->
          if (actionType == SecurityActionType.INHERITANCE) {
            return@mapNotNull null
          }
          MetricTrackerServiceFake.CompletedMetric(
            metric = TrackedMetric(
              name = SecurityActionMetricDefinition(actionType).name,
              variant = null
            ),
            outcome = MetricOutcome.Succeeded
          )
        }
      )
      eventTracker.eventCalls.skipItems(12)
      cancelAndIgnoreRemainingEvents()
    }
  }
})
