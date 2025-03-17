package build.wallet.statemachine.dev

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface FirmwareMetadataUiStateMachine : StateMachine<FirmwareMetadataUiProps, ScreenModel>

data class FirmwareMetadataUiProps(
  val onBack: () -> Unit,
)
