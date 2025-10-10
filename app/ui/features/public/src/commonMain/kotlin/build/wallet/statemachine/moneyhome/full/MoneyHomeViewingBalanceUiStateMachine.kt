package build.wallet.statemachine.moneyhome.full

import build.wallet.bitkey.account.Account
import build.wallet.partnerships.PartnerInfo
import build.wallet.partnerships.PartnershipTransaction
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData
import build.wallet.ui.model.status.StatusBannerModel

/**
 * State machine for the screen to show when the state of the Money Home screen is
 * [ViewingBalanceUiState].
 *
 * The overall Money Home experience (including showing screens for other states) is managed by
 * [MoneyHomeUiStateMachine], this is a child state machine specifically for when the balance is
 * showing.
 */
interface MoneyHomeViewingBalanceUiStateMachine :
  StateMachine<MoneyHomeViewingBalanceUiProps, ScreenModel>

data class MoneyHomeViewingBalanceUiProps(
  val account: Account,
  val lostHardwareRecoveryData: LostHardwareRecoveryData,
  val homeStatusBannerModel: StatusBannerModel?,
  val onSettings: () -> Unit,
  val onPartnershipsWebFlowCompleted: (PartnerInfo, PartnershipTransaction) -> Unit,
  val state: MoneyHomeUiState.ViewingBalanceUiState,
  val setState: (MoneyHomeUiState) -> Unit,
  val onStartSweepFlow: () -> Unit,
  val onGoToSecurityHub: () -> Unit,
  val onGoToPrivateWalletMigration: () -> Unit,
)
