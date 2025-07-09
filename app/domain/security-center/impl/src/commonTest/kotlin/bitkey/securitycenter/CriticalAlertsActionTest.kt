package bitkey.securitycenter

import bitkey.notifications.NotificationChannel
import bitkey.notifications.NotificationsService
import build.wallet.availability.FunctionalityFeatureStates
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Available
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class CriticalAlertsActionTest :
  FunSpec({
    test("Test CriticalAlertsAction with no missing channels") {
      val noitificationStatus = NotificationsService.NotificationStatus.Enabled
      val healthyAction = CriticalAlertsAction(noitificationStatus, Available)
      healthyAction.getRecommendations().shouldBeEmpty()
      healthyAction.category() shouldBe SecurityActionCategory.RECOVERY
      healthyAction.state() shouldBe SecurityActionState.Secure
    }

    test("Test CriticalAlertsAction with missing channels") {
      val noitificationStatus = NotificationsService.NotificationStatus.Missing(
        setOf(NotificationChannel.Push, NotificationChannel.Email, NotificationChannel.Sms)
      )
      val action = CriticalAlertsAction(noitificationStatus, Available)
      action.getRecommendations() shouldBe listOf(
        SecurityActionRecommendation.ENABLE_PUSH_NOTIFICATIONS,
        SecurityActionRecommendation.ENABLE_EMAIL_NOTIFICATIONS,
        SecurityActionRecommendation.ENABLE_SMS_NOTIFICATIONS
      )
      action.category() shouldBe SecurityActionCategory.RECOVERY
      action.state() shouldBe SecurityActionState.HasCriticalActions
    }

    test("Test CriticalAlertsAction with error") {
      val noitificationStatus = NotificationsService.NotificationStatus.Error(Throwable())
      val action = CriticalAlertsAction(noitificationStatus, Available)
      action.getRecommendations() shouldBe listOf(SecurityActionRecommendation.ENABLE_CRITICAL_ALERTS)
      action.category() shouldBe SecurityActionCategory.RECOVERY
      action.state() shouldBe SecurityActionState.HasRecommendationActions
    }

    test("Test CriticalAlertsAction with disabled feature") {
      val noitificationStatus = NotificationsService.NotificationStatus.Enabled
      val action = CriticalAlertsAction(noitificationStatus, FunctionalityFeatureStates.FeatureState.Unavailable)
      action.getRecommendations().shouldBeEmpty()
      action.category() shouldBe SecurityActionCategory.RECOVERY
      action.state() shouldBe SecurityActionState.Disabled
    }
  })
