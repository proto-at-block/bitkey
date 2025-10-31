package build.wallet.statemachine.settings.full

import build.wallet.bitkey.account.Account
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachineImpl.SettingsListState
import build.wallet.ui.model.status.StatusBannerModel

interface SettingsHomeUiStateMachine : StateMachine<SettingsHomeUiProps, ScreenModel>

/**
 * @param onBack callback for back button
 * @param account the account
 * @param settingsListState starting state for the settings list, if any
 * @param homeStatusBannerModel status banner displayed, if any
 */
data class SettingsHomeUiProps(
  val onBack: () -> Unit,
  val account: Account,
  val settingsListState: SettingsListState?,
  val homeStatusBannerModel: StatusBannerModel?,
  val goToSecurityHub: () -> Unit,
)
