package build.wallet.statemachine.platform.permissions

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel

data class RequestPermissionBodyModel(
  val title: String,
  val explanation: String,
  val showingSystemPermission: Boolean,
  override val onBack: () -> Unit,
  val onRequest: () -> Unit,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel()
