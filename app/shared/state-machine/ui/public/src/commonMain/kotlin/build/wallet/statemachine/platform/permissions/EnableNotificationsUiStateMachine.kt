package build.wallet.statemachine.platform.permissions

import build.wallet.analytics.events.screen.context.PushNotificationEventTrackerScreenIdContext
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.StateMachine

interface EnableNotificationsUiStateMachine : StateMachine<EnableNotificationsUiProps, BodyModel>

/**
 * @property eventTrackerContext context for screen events emitted by this state machine to
 * disambiguate
 */
data class EnableNotificationsUiProps(
  val retreat: Retreat,
  val onComplete: () -> Unit,
  val eventTrackerContext: PushNotificationEventTrackerScreenIdContext,
)
