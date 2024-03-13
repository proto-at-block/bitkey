package build.wallet.statemachine.moneyhome.lite

import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.AccountData
import build.wallet.ui.model.status.StatusBannerModel
import com.github.michaelbull.result.Result
import kotlinx.collections.immutable.ImmutableList

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
  val accountData: AccountData.HasActiveLiteAccountData,
  val protectedCustomers: ImmutableList<ProtectedCustomer>,
  val homeStatusBannerModel: StatusBannerModel?,
  val onRemoveRelationship: suspend (ProtectedCustomer) -> Result<Unit, Error>,
  val onSettings: () -> Unit,
  val onAcceptInvite: () -> Unit,
)
