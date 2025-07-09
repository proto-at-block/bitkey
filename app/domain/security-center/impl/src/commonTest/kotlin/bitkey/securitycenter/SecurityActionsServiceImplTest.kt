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
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock

class SecurityActionsServiceImplTest : FunSpec({
  val eventTracker = EventTrackerMock(turbines::create)
  val metricTrackerService = MetricTrackerServiceFake()
  val securityRecommendationInteractionDao = SecurityRecommendationInteractionDaoMock()
  val clock = Clock.System

  beforeTest {
    metricTrackerService.reset()
  }

  test("get security actions with at risk recommendations") {
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

    service.executeWork()

    service.securityActionsWithRecommendations.test {
      awaitItem().apply {
        securityActions.map { it.type() }.shouldContainExactly(
          SecurityActionType.HARDWARE_DEVICE,
          SecurityActionType.FINGERPRINTS,
          SecurityActionType.BIOMETRIC,
          SecurityActionType.TRANSACTION_VERIFICATION
        )
        recoveryActions.map { it.type() }.shouldContainExactly(
          SecurityActionType.CRITICAL_ALERTS,
          SecurityActionType.SOCIAL_RECOVERY,
          SecurityActionType.APP_KEY_BACKUP,
          SecurityActionType.EEK_BACKUP
        )
        recommendations.shouldBeEmpty()

        atRiskRecommendations.shouldContainExactly(
          SecurityActionRecommendation.PAIR_HARDWARE_DEVICE,
          SecurityActionRecommendation.BACKUP_MOBILE_KEY
        )
      }
    }

    SecurityActionRecommendation.entries.forEach { recommendation ->
      if (recommendation == SecurityActionRecommendation.ADD_BENEFICIARY) {
        return@forEach
      }
      val isPending = when (recommendation) {
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
  }

  test("get security actions with no at risk recommendations") {
    val service = SecurityActionsServiceImpl(
      AppKeyCloudBackupHealthActionFactoryFake().also {
        it.includeRecommendations = false
      },
      EekCloudBackupHealthActionFactoryFake(),
      SocialRecoveryActionFactoryFake(),
      BiometricActionFactoryFake(),
      CriticalAlertsActionFactoryFake(),
      FingerprintsActionFactoryFake(),
      HardwareDeviceActionFactoryFake().also {
        it.includeRecommendations = false
      },
      TxVerificationActionFactoryFake(),
      eventTracker,
      metricTrackerService,
      securityRecommendationInteractionDao,
      clock
    )

    service.executeWork()

    service.securityActionsWithRecommendations.test {
      awaitItem().apply {
        securityActions.map { it.type() }.shouldContainExactly(
          SecurityActionType.HARDWARE_DEVICE,
          SecurityActionType.FINGERPRINTS,
          SecurityActionType.BIOMETRIC,
          SecurityActionType.TRANSACTION_VERIFICATION
        )
        recoveryActions.map { it.type() }.shouldContainExactly(
          SecurityActionType.CRITICAL_ALERTS,
          SecurityActionType.SOCIAL_RECOVERY,
          SecurityActionType.APP_KEY_BACKUP,
          SecurityActionType.EEK_BACKUP
        )
        recommendations.shouldContainExactlyInAnyOrder(
          SecurityActionRecommendation.BACKUP_EAK,
          SecurityActionRecommendation.ADD_FINGERPRINTS,
          SecurityActionRecommendation.COMPLETE_FINGERPRINT_RESET,
          SecurityActionRecommendation.ADD_TRUSTED_CONTACTS,
          SecurityActionRecommendation.ENABLE_CRITICAL_ALERTS,
          SecurityActionRecommendation.SETUP_BIOMETRICS,
          SecurityActionRecommendation.ENABLE_TRANSACTION_VERIFICATION
        )

        atRiskRecommendations.shouldBeEmpty()
      }
    }

    SecurityActionRecommendation.entries.forEach { recommendation ->
      if (recommendation == SecurityActionRecommendation.ADD_BENEFICIARY) {
        return@forEach
      }
      val isPending = when (recommendation) {
        SecurityActionRecommendation.ENABLE_PUSH_NOTIFICATIONS -> false
        SecurityActionRecommendation.ENABLE_EMAIL_NOTIFICATIONS -> false
        SecurityActionRecommendation.ENABLE_SMS_NOTIFICATIONS -> false
        SecurityActionRecommendation.PAIR_HARDWARE_DEVICE -> false
        SecurityActionRecommendation.BACKUP_MOBILE_KEY -> false
        SecurityActionRecommendation.UPDATE_FIRMWARE -> false
        else -> true
      }

      eventTracker.eventCalls.awaitItem() shouldBe TrackedAction(
        action = Action.ACTION_APP_SECURITY_CENTER_CHECK,
        screenId = null,
        context = SecurityCenterScreenIdContext(recommendation, isPending = isPending)
      )
    }

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
  }
})
