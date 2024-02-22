package build.wallet.statemachine.dev.logs

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel

/**
 * Model for showing screen with logs and various debugging options.
 *
 * @property errorsOnly - if `true`, only error logs will be shown (well... warn as well).
 * @property onClear - if called, in-memory logs will be wiped.
 */
data class LogsBodyModel(
  val errorsOnly: Boolean,
  var analyticsEventsOnly: Boolean,
  val onErrorsOnlyValueChanged: (errorsOnly: Boolean) -> Unit,
  val onAnalyticsEventsOnlyValueChanged: (analyticsEventsOnly: Boolean) -> Unit,
  val onClear: () -> Unit,
  val logsModel: LogsModel,
  override val onBack: () -> Unit,
  // This is only used by the debug menu, it doesn't need a screen ID
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel()
