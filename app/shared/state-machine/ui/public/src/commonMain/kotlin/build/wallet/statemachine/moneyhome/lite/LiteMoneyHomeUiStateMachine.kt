package build.wallet.statemachine.moneyhome.lite

import build.wallet.bitkey.account.LiteAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.status.StatusBannerModel

/**
 * State machine for managing the Money Home experience for lite (as opposed to full)
 * customers. Right now, "lite" customers are customers that only act as trusted
 * contacts for "full" customers.
 */
interface LiteMoneyHomeUiStateMachine : StateMachine<LiteMoneyHomeUiProps, ScreenModel>

/**
 * @property homeStatusBannerModel status banner to show on root Money Home screen
 * @property onSettings Settings tab item clicked
 */
data class LiteMoneyHomeUiProps(
  val account: LiteAccount,
  val onUpgradeAccount: () -> Unit,
  val homeStatusBannerModel: StatusBannerModel?,
  val onSettings: () -> Unit,
  val onAcceptInvite: () -> Unit,
  val onBecomeBeneficiary: () -> Unit,
)
