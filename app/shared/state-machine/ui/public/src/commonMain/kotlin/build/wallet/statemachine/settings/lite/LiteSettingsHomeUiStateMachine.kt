package build.wallet.statemachine.settings.lite

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.AccountData
import build.wallet.ui.model.status.StatusBannerModel

interface LiteSettingsHomeUiStateMachine : StateMachine<LiteSettingsHomeUiProps, ScreenModel>

/**
 * @param homeStatusBannerModel status banner to show when showing all settings
 */
data class LiteSettingsHomeUiProps(
  val onBack: () -> Unit,
  val accountData: AccountData.HasActiveLiteAccountData,
  val homeStatusBannerModel: StatusBannerModel?,
)
