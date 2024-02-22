package build.wallet.statemachine.money.amount

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel

data class MoneyAmountEntryModel(
  val primaryAmount: String,
  val primaryAmountGhostedSubstringRange: IntRange?,
  val secondaryAmount: String?,
  // We don't want to track this for privacy reasons
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel()
