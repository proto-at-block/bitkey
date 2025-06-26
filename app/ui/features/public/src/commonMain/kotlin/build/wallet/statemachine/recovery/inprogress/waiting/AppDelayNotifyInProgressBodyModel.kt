package build.wallet.statemachine.recovery.inprogress.waiting

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.Progress
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.TimerDirection.CounterClockwise
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.Timer
import build.wallet.ui.app.recovery.AppDelayNotifyInProgressScreen
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Treatment.TertiaryDestructive
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.ButtonAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlin.time.Duration

/**
 * Model to represent the screen in which the user is waiting for their
 * delay time to pass so that they can recover their App Key or reset fingerprints.
 * https://www.figma.com/file/XH6G74MVgS7x0WGvomq80f/Lost-Mobile-Key?node-id=354%3A19029&t=8ZQiMO2gZLaoROx7-0
 */
data class AppDelayNotifyInProgressBodyModel(
  val headline: String = "Recovery in progress...",
  val delayInfoText: String = "Your new App Key will be ready for creation on this phone after our security delay.",
  val cancelWarningText: String = "If you didn’t authorize this replacement, press “Cancel recovery” above.",
  val cancelText: String = "Cancel recovery",
  val onStopRecovery: () -> Unit,
  val durationTitle: String,
  val progress: Progress,
  val remainingDelayPeriod: Duration,
  val onExit: (() -> Unit)?,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? =
    EventTrackerScreenInfo(
      eventTrackerScreenId = DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_PENDING
    ),
) : BodyModel() {
  val toolbar = ToolbarModel(
    leadingAccessory = onExit?.let {
      IconAccessory.CloseAccessory(onClick = onExit)
    },
    trailingAccessory = ButtonAccessory(
      model = ButtonModel(
        text = cancelText,
        treatment = TertiaryDestructive,
        size = Compact,
        onClick = StandardClick { onStopRecovery() }
      )
    )
  )
  val header = FormHeaderModel(
    headline = headline,
    subline = buildString {
      append(
        delayInfoText
      )
      appendLine()
      appendLine()
      appendLine(
        cancelWarningText
      )
    }
  )
  val timerModel = Timer(
    title = durationTitle,
    subtitle = "remaining",
    timerProgress = progress,
    direction = CounterClockwise,
    timerRemainingSeconds = remainingDelayPeriod.inWholeSeconds
  )

  @Composable
  override fun render(modifier: Modifier) {
    AppDelayNotifyInProgressScreen(modifier, model = this)
  }
}
