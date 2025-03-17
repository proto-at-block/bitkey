package build.wallet.statemachine.recovery.cloud

import build.wallet.statemachine.BodyStateMachineMock
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
class CloudSignInUiStateMachineMock :
  CloudSignInUiStateMachine,
  BodyStateMachineMock<CloudSignInUiProps>(
    id = "cloud-sign-in"
  )
