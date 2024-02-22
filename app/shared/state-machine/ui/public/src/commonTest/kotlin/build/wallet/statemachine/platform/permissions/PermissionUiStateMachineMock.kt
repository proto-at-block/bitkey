package build.wallet.statemachine.platform.permissions

import build.wallet.statemachine.BodyStateMachineMock

class PermissionUiStateMachineMock(
  override var isImplemented: Boolean = true,
) :
  PermissionUiStateMachine, BodyStateMachineMock<PermissionUiProps>(id = "permission")
