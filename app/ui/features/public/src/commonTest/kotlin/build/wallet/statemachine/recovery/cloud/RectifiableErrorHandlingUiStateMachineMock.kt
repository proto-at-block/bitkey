package build.wallet.statemachine.recovery.cloud

import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.cloud.RectifiableErrorHandlingProps
import build.wallet.statemachine.cloud.RectifiableErrorHandlingUiStateMachine
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
class RectifiableErrorHandlingUiStateMachineMock :
  RectifiableErrorHandlingUiStateMachine,
  ScreenStateMachineMock<RectifiableErrorHandlingProps>(
    id = "rectifiable-error-handling"
  )
