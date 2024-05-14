package bitkey.sample.ui.settings

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import kotlinx.collections.immutable.ImmutableList

data class SettingsListBodyModel(
  override val onBack: () -> Unit,
  val rows: ImmutableList<SettingsRowModel>,
) : BodyModel() {
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null

  data class SettingsRowModel(
    val title: String,
    val onClick: () -> Unit,
  )
}
