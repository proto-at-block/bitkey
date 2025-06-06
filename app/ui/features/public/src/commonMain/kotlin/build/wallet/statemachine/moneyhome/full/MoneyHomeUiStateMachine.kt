package build.wallet.statemachine.moneyhome.full

import build.wallet.bitkey.account.Account
import build.wallet.partnerships.*
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData
import build.wallet.ui.model.status.StatusBannerModel

/**
 * State machine for managing the Money Home experience for full (as opposed to lite)
 * customers. "Full" customers are customers that have a hardware device (and wallet),
 * and optionally are also a Recovery Contact for other "full" customers.
 */
interface MoneyHomeUiStateMachine : StateMachine<MoneyHomeUiProps, ScreenModel>

/**
 * @property homeBottomSheetModel bottom sheet to show on root Money Home screen
 * @property homeStatusBannerModel status banner to show on root Money Home screen
 * @property onSettings Settings tab item clicked
 * @property origin The origin of money home. Used to control modals that only show during launch.
 */
data class MoneyHomeUiProps(
  val account: Account,
  val lostHardwareRecoveryData: LostHardwareRecoveryData,
  val homeBottomSheetModel: SheetModel?,
  val homeStatusBannerModel: StatusBannerModel?,
  val onSettings: () -> Unit,
  val onPartnershipsWebFlowCompleted: (PartnerInfo, PartnershipTransaction) -> Unit,
  val origin: Origin,
  val onDismissOrigin: () -> Unit,
  val onGoToSecurityHub: () -> Unit,
) {
  sealed class Origin {
    data object Launch : Origin()

    data object Settings : Origin()

    data object SecurityHub : Origin()

    data object LostHardwareRecovery : Origin()

    data class PartnershipsSell(
      val partnerId: PartnerId?,
      val event: PartnershipEvent?,
      val partnerTransactionId: PartnershipTransactionId?,
    ) : Origin()
  }
}
