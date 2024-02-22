package build.wallet.statemachine.receive

import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData

/**
 * A state machine with receiving functionality (wallet address, QR code).
 */
interface AddressQrCodeUiStateMachine : StateMachine<AddressQrCodeUiProps, BodyModel>

/**
 * @property keybox - used to derive the most appropriate receiving address.
 */
data class AddressQrCodeUiProps(
  val accountData: ActiveFullAccountLoadedData,
  val onBack: () -> Unit,
)
