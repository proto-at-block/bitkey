package build.wallet.statemachine.platform.permissions

import build.wallet.statemachine.BodyStateMachineMock
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
class PermissionUiStateMachineMock(
  override var isImplemented: Boolean = true,
) :
  PermissionUiStateMachine, BodyStateMachineMock<PermissionUiProps>(id = "permission")
