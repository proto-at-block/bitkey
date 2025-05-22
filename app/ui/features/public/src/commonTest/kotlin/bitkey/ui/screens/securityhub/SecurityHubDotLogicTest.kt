package bitkey.ui.screens.securityhub

import bitkey.securitycenter.SecurityActionRecommendation
import bitkey.securitycenter.SecurityActionsServiceFake
import bitkey.securitycenter.SecurityRecommendationWithStatus
import build.wallet.database.SecurityInteractionStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock

class SecurityHubDotLogicTest : FunSpec({
  val testClock = Clock.System
  val service = SecurityActionsServiceFake()

  fun recWithStatus(
    type: SecurityActionRecommendation,
    viewed: Boolean,
  ): SecurityRecommendationWithStatus {
    return SecurityRecommendationWithStatus(
      recommendation = type,
      interactionStatus = if (viewed) SecurityInteractionStatus.VIEWED else SecurityInteractionStatus.NEW,
      lastRecommendationTriggeredAt = testClock.now(),
      lastInteractedAt = if (viewed) testClock.now() else null,
      recordUpdatedAt = testClock.now()
    )
  }

  test("dot not shown if all recommendations viewed") {
    service.statuses = SecurityActionRecommendation.entries.map {
      recWithStatus(it, viewed = true)
    }
    val attention = runBlocking { service.hasRecommendationsRequiringAttention().first() }
    attention shouldBe false
  }

  test("dot is shown if some recommendations not viewed") {
    service.statuses = SecurityActionRecommendation.entries.mapIndexed { i, it ->
      recWithStatus(it, viewed = i % 2 == 0)
    }
    val attention = runBlocking { service.hasRecommendationsRequiringAttention().first() }
    attention shouldBe true
  }
})
