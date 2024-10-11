package build.wallet.statemachine.recovery.inprogress.waiting

import build.wallet.Progress
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_PENDING
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.TimerDirection.CounterClockwise
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.Timer
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Size.Regular
import build.wallet.ui.model.button.ButtonModel.Treatment.Secondary
import build.wallet.ui.model.button.ButtonModel.Treatment.TertiaryDestructive
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.ButtonAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlin.time.Duration

data class HardwareDelayNotifyInProgressScreenModel(
  val onCancelRecovery: () -> Unit,
  val durationTitle: String,
  val progress: Progress,
  val remainingDelayPeriod: Duration,
  val onExit: () -> Unit,
) : FormBodyModel(
    id = LOST_HW_DELAY_NOTIFY_PENDING,
    onBack = onExit,
    toolbar = ToolbarModel(
      leadingAccessory = CloseAccessory(onClick = onExit),
      trailingAccessory = ButtonAccessory(
        ButtonModel(
          text = "Cancel recovery",
          treatment = TertiaryDestructive,
          onClick = StandardClick { onCancelRecovery() },
          size = Regular
        )
      )
    ),
    header = FormHeaderModel(
      headline = "Replacement in progress...",
      subline = buildString {
        appendLine("Your new Bitkey device will be ready after the security waiting period.")
        appendLine()
        appendLine("If you didn’t authorize this replacement, press “Cancel recovery” above.")
      }
    ),
    mainContentList = immutableListOf(
      Timer(
        title = durationTitle,
        subtitle = "remaining",
        timerProgress = progress,
        direction = CounterClockwise,
        timerRemainingSeconds = remainingDelayPeriod.inWholeSeconds
      )
    ),
    primaryButton = null,
    secondaryButton = ButtonModel(
      text = "Done",
      treatment = Secondary,
      size = Footer,
      onClick = StandardClick { onExit() }
    )
  )
