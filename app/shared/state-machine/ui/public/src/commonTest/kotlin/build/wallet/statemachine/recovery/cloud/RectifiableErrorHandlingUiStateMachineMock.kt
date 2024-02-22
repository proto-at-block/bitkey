package build.wallet.statemachine.recovery.cloud

import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.cloud.RectifiableErrorHandlingProps
import build.wallet.statemachine.cloud.RectifiableErrorHandlingUiStateMachine

class RectifiableErrorHandlingUiStateMachineMock :
  RectifiableErrorHandlingUiStateMachine,
  ScreenStateMachineMock<RectifiableErrorHandlingProps>(
    id = "rectifiable-error-handling"
  )
