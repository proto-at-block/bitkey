package build.wallet.statemachine.platform.permissions

import androidx.compose.runtime.Composable
import build.wallet.analytics.events.EventTrackerContext
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer

@BitkeyInject(ActivityScope::class)
class EnableNotificationsUiStateMachineImpl : EnableNotificationsUiStateMachine {
  @Composable
  override fun model(props: EnableNotificationsUiProps): BodyModel {
    return EnableNotificationsBodyModel(
      eventTrackerContext = props.eventTrackerContext,
      onBack = props.retreat.onRetreat,
      onComplete = props.onComplete
    )
  }
}

data class EnableNotificationsBodyModel(
  override val eventTrackerContext: EventTrackerContext,
  val onComplete: () -> Unit,
  override val onBack: () -> Unit,
) : FormBodyModel(
    id = NotificationsEventTrackerScreenId.ENABLE_PUSH_NOTIFICATIONS,
    eventTrackerContext = eventTrackerContext,
    onBack = onBack,
    toolbar = null,
    header =
      FormHeaderModel(
        headline = "Turn on notifications"
      ),
    primaryButton =
      ButtonModel(
        text = "Enable notifications",
        isEnabled = true,
        isLoading = false,
        size = Footer,
        onClick = StandardClick(onComplete)
      )
  )
