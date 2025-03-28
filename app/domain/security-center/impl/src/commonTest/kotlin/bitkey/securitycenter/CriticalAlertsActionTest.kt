package bitkey.securitycenter

import bitkey.notifications.NotificationChannel
import bitkey.notifications.NotificationsService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class CriticalAlertsActionTest :
  FunSpec({
    test("Test CriticalAlertsAction with no missing channels") {
      val noitificationStatus = NotificationsService.NotificationStatus.Enabled
      val healthyAction = CriticalAlertsAction(noitificationStatus)
      healthyAction.getRecommendations().shouldBeEmpty()
      healthyAction.category() shouldBe SecurityActionCategory.RECOVERY
    }

    test("Test CriticalAlertsAction with missing channels") {
      val noitificationStatus = NotificationsService.NotificationStatus.Missing(
        setOf(NotificationChannel.Push)
      )
      val action = CriticalAlertsAction(noitificationStatus)
      action.getRecommendations() shouldBe listOf(SecurityActionRecommendation.ENABLE_CRITICAL_ALERTS)
      action.category() shouldBe SecurityActionCategory.RECOVERY
    }
  })
