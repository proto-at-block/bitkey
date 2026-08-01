package build.wallet.statemachine.send.utxo

import build.wallet.bitcoin.utxo.CoinControl
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * Optional advanced affordance for picking confirmed UTXOs during send.
 *
 * Not a mandatory SendUi step. Hosted from amount entry as a nested screen.
 */
interface UtxoSelectionUiStateMachine : StateMachine<UtxoSelectionUiProps, ScreenModel>

/**
 * @property targetAmount send amount used for a soft underfunded check when ExactAmount;
 *   null skips the soft check (e.g. SendAll).
 * @property initialSelection previously chosen control to pre-check, or null for a fresh session.
 * @property onConfirm validated [CoinControl] from confirmed inventory.
 * @property onClear clear selection back to Automatic (parent sets null).
 * @property onBack dismiss without changing parent selection.
 */
data class UtxoSelectionUiProps(
  val targetAmount: BitcoinMoney? = null,
  val initialSelection: CoinControl? = null,
  val onConfirm: (CoinControl) -> Unit,
  val onClear: () -> Unit,
  val onBack: () -> Unit,
)
