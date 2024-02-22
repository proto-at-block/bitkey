package build.wallet.statemachine.recovery.cloud

import build.wallet.statemachine.BodyStateMachineMock

class CloudSignInUiStateMachineMock :
  CloudSignInUiStateMachine,
  BodyStateMachineMock<CloudSignInUiProps>(
    id = "cloud-sign-in"
  )
