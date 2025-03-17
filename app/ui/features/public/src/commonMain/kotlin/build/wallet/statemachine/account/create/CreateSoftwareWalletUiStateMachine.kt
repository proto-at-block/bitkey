package build.wallet.statemachine.account.create

import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * UI flow for creating a new software wallet.
 */
interface CreateSoftwareWalletUiStateMachine : StateMachine<CreateSoftwareWalletProps, ScreenModel>

data class CreateSoftwareWalletProps(
  val onExit: () -> Unit,
  val onSuccess: (SoftwareAccount) -> Unit,
)
