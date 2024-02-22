package build.wallet.statemachine.recovery.inprogress.waiting

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.AppRecoveryEventTrackerScreenId
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.TimerDirection.CounterClockwise
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.Timer
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Treatment.TertiaryDestructive
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.ButtonAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlin.time.Duration

/**
 * Model to represent the screen in which the user is waiting for their
 * delay time to pass so that they can recover their mobile key
 * https://www.figma.com/file/XH6G74MVgS7x0WGvomq80f/Lost-Mobile-Key?node-id=354%3A19029&t=8ZQiMO2gZLaoROx7-0
 */
data class AppDelayNotifyInProgressBodyModel(
  val toolbar: ToolbarModel,
  val header: FormHeaderModel,
  val timerModel: Timer,
  val onExit: (() -> Unit)?,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? =
    EventTrackerScreenInfo(
      eventTrackerScreenId = AppRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_PENDING
    ),
) : BodyModel() {
  constructor(
    onStopRecovery: () -> Unit,
    durationTitle: String,
    progress: Float,
    remainingDelayPeriod: Duration,
    onExit: (() -> Unit)?,
  ) : this(
    toolbar =
      ToolbarModel(
        leadingAccessory =
          onExit?.let {
            IconAccessory.CloseAccessory(onClick = onExit)
          },
        trailingAccessory =
          ButtonAccessory(
            model =
              ButtonModel(
                text = "Cancel recovery",
                treatment = TertiaryDestructive,
                size = Compact,
                onClick = Click.standardClick { onStopRecovery() }
              )
          )
      ),
    header =
      FormHeaderModel(
        headline = "Recovery in progress...",
        subline =
          buildString {
            append(
              "Your new mobile key will be ready for creation on this phone after our security delay."
            )
            appendLine()
            appendLine()
            appendLine(
              "If you didn’t authorize this replacement, press “Cancel Recovery” above."
            )
          }
      ),
    timerModel =
      Timer(
        title = durationTitle,
        subtitle = "remaining",
        timerProgress = progress,
        direction = CounterClockwise,
        timerRemainingSeconds = remainingDelayPeriod.inWholeSeconds
      ),
    onExit = onExit
  )
}
