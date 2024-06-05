package build.wallet.statemachine.settings.full.device.resetdevice.intro

import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitkey.keybox.Keybox
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine to the intro screen, and it's various modals for resetting a Bitkey device.
 */
interface ResettingDeviceIntroUiStateMachine : StateMachine<ResettingDeviceIntroProps, ScreenModel>

data class ResettingDeviceIntroProps(
  val onBack: () -> Unit,
  val onUnwindToMoneyHome: () -> Unit,
  val onDeviceConfirmed: () -> Unit,
  val spendingWallet: SpendingWallet,
  val keybox: Keybox,
  val balance: BitcoinBalance,
  val isHardwareFake: Boolean,
)
