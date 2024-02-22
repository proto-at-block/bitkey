package build.wallet.statemachine.data.notifications

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.StateMachine

interface NotificationTouchpointDataStateMachine : StateMachine<NotificationTouchpointProps, NotificationTouchpointData>

data class NotificationTouchpointProps(
  val account: FullAccount,
)
