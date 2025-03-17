package build.wallet.statemachine.moneyhome.card.fwup

import build.wallet.fwup.FirmwareData
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.moneyhome.card.CardModel

/**
 * State machine which renders a [CardModel] representing "Device Update" in [MoneyHomeStateMachine].
 * The model is null when there is no card to show
 */
interface DeviceUpdateCardUiStateMachine : StateMachine<DeviceUpdateCardUiProps, CardModel?>

/**
 * @property onUpdateDevice invoked when card is tapped
 */
data class DeviceUpdateCardUiProps(
  val onUpdateDevice: (FirmwareData.FirmwareUpdateState.PendingUpdate) -> Unit,
)
