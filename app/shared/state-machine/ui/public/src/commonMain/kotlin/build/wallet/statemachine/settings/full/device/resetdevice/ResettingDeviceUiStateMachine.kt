package build.wallet.statemachine.settings.full.device.resetdevice

import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine to present screens for resetting a Bitkey device.
 */
interface ResettingDeviceUiStateMachine : StateMachine<ResettingDeviceProps, ScreenModel>

data class ResettingDeviceProps(
  val onBack: () -> Unit,
  val onSuccess: () -> Unit,
  val fullAccountConfig: FullAccountConfig,
  val fullAccount: FullAccount?,
  val spendingWallet: SpendingWallet?,
  val balance: BitcoinBalance?,
)
