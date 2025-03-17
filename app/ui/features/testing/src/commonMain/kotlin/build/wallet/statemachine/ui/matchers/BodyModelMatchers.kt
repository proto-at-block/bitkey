package build.wallet.statemachine.ui.matchers

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.statemachine.core.BodyModel
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Verify that [BodyModel] has [EventTrackerScreenInfo] with the given [EventTrackerScreenId].
 */
fun <T : BodyModel> T.shouldHaveId(id: EventTrackerScreenId): T =
  apply {
    this.eventTrackerScreenInfo
      .shouldNotBeNull()
      .eventTrackerScreenId
      .shouldBe(id)
  }
