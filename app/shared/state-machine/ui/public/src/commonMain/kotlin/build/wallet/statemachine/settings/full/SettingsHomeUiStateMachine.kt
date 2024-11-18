package build.wallet.statemachine.settings.full

import build.wallet.bitkey.account.Account
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData
import build.wallet.ui.model.status.StatusBannerModel

interface SettingsHomeUiStateMachine : StateMachine<SettingsHomeUiProps, ScreenModel>

/**
 * @param homeBottomSheetModel bottom sheet to show when showing all settings
 * @param homeStatusBannerModel status banner to show when showing all settings
 */
data class SettingsHomeUiProps(
  val onBack: () -> Unit,
  val account: Account,
  val lostHardwareRecoveryData: LostHardwareRecoveryData,
  val homeBottomSheetModel: SheetModel?,
  val homeStatusBannerModel: StatusBannerModel?,
)
