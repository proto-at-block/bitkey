package build.wallet.statemachine.settings.full

import build.wallet.f8e.socrec.SocRecRelationships
import build.wallet.recovery.socrec.SocRecProtectedCustomerActions
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.firmware.FirmwareData
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData
import build.wallet.statemachine.data.sync.ElectrumServerData
import build.wallet.ui.model.status.StatusBannerModel

interface SettingsHomeUiStateMachine : StateMachine<SettingsHomeUiProps, ScreenModel>

/**
 * @param homeBottomSheetModel bottom sheet to show when showing all settings
 * @param homeStatusBannerModel status banner to show when showing all settings
 */
data class SettingsHomeUiProps(
  val onBack: () -> Unit,
  val accountData: ActiveFullAccountLoadedData,
  val electrumServerData: ElectrumServerData,
  val firmwareData: FirmwareData,
  val socRecRelationships: SocRecRelationships,
  val socRecActions: SocRecProtectedCustomerActions,
  val homeBottomSheetModel: SheetModel?,
  val homeStatusBannerModel: StatusBannerModel?,
)
