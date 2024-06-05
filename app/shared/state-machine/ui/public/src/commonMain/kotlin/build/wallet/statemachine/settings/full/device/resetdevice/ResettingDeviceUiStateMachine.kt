package build.wallet.statemachine.settings.full.device.resetdevice

import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitkey.keybox.Keybox
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine to present screens for resetting a Bitkey device.
 */
interface ResettingDeviceUiStateMachine : StateMachine<ResettingDeviceProps, ScreenModel>

data class ResettingDeviceProps(
  val onBack: () -> Unit,
  val onUnwindToMoneyHome: () -> Unit,
  val spendingWallet: SpendingWallet,
  val keybox: Keybox,
  val balance: BitcoinBalance,
  val isHardwareFake: Boolean,
)
