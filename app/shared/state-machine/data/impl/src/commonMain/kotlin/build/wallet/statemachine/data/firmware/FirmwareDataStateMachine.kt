package build.wallet.statemachine.data.firmware

import build.wallet.statemachine.core.StateMachine

interface FirmwareDataStateMachine : StateMachine<FirmwareDataProps, FirmwareData>

data class FirmwareDataProps(
  val isHardwareFake: Boolean,
)
