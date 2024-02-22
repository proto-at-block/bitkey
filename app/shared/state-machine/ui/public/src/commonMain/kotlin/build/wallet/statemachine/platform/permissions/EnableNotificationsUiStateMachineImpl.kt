package build.wallet.statemachine.platform.permissions

import build.wallet.analytics.events.EventTracker
import build.wallet.platform.permissions.PermissionChecker

expect class EnableNotificationsUiStateMachineImpl(
  notificationPermissionRequester: NotificationPermissionRequester,
  permissionChecker: PermissionChecker,
  eventTracker: EventTracker,
) : EnableNotificationsUiStateMachine
