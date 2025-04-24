package build.wallet.statemachine.settings.full

import build.wallet.bitkey.account.Account
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachineImpl.SettingsListState
import build.wallet.ui.model.status.StatusBannerModel

interface SettingsHomeUiStateMachine : StateMachine<SettingsHomeUiProps, ScreenModel>

/**
 * @param onBack callback for back button
 * @param account the account
 * @param settingsListState starting state for the settings list, if any
 * @param lostHardwareRecoveryData data for lost hardware recovery
 * @param homeBottomSheetModel bottom sheet displayed, if any
 * @param homeStatusBannerModel status banner displayed, if any
 */
data class SettingsHomeUiProps(
  val onBack: () -> Unit,
  val account: Account,
  val settingsListState: SettingsListState?,
  val lostHardwareRecoveryData: LostHardwareRecoveryData,
  val homeBottomSheetModel: SheetModel?,
  val homeStatusBannerModel: StatusBannerModel?,
  val goToSecurityHub: () -> Unit,
)
