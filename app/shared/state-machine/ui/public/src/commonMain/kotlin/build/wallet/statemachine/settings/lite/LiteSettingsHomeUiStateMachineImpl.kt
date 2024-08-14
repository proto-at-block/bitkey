package build.wallet.statemachine.settings.lite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.money.BitcoinMoney
import build.wallet.platform.config.AppVariant
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.dev.DebugMenuProps
import build.wallet.statemachine.dev.DebugMenuStateMachine
import build.wallet.statemachine.money.currency.CurrencyPreferenceProps
import build.wallet.statemachine.money.currency.CurrencyPreferenceUiStateMachine
import build.wallet.statemachine.recovery.socrec.LiteTrustedContactManagementProps
import build.wallet.statemachine.recovery.socrec.LiteTrustedContactManagementUiStateMachine
import build.wallet.statemachine.settings.SettingsListUiProps
import build.wallet.statemachine.settings.SettingsListUiStateMachine
import build.wallet.statemachine.settings.full.feedback.FeedbackUiProps
import build.wallet.statemachine.settings.full.feedback.FeedbackUiStateMachine
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiProps
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiStateMachine
import build.wallet.statemachine.settings.showDebugMenu
import build.wallet.ui.model.alert.ButtonAlertModel

class LiteSettingsHomeUiStateMachineImpl(
  private val appVariant: AppVariant,
  private val currencyPreferenceUiStateMachine: CurrencyPreferenceUiStateMachine,
  private val feedbackUiStateMachine: FeedbackUiStateMachine,
  private val helpCenterUiStateMachine: HelpCenterUiStateMachine,
  private val liteTrustedContactManagementUiStateMachine:
    LiteTrustedContactManagementUiStateMachine,
  private val settingsListUiStateMachine: SettingsListUiStateMachine,
  private val debugMenuStateMachine: DebugMenuStateMachine,
) : LiteSettingsHomeUiStateMachine {
  @Composable
  override fun model(props: LiteSettingsHomeUiProps): ScreenModel {
    var uiState: State by remember { mutableStateOf(State.ShowingAllSettingsList) }

    return when (uiState) {
      State.ShowingAllSettingsList ->
        ShowingAllSettingsListScreen(
          props = props,
          setState = { uiState = it }
        )

      is State.ShowingCurrencyPreferenceSettings ->
        currencyPreferenceUiStateMachine.model(
          props =
            CurrencyPreferenceProps(
              onBack = { uiState = State.ShowingAllSettingsList },
              btcDisplayAmount = BitcoinMoney.zero()
            )
        )

      is State.ShowingTrustedContactsManagement ->
        liteTrustedContactManagementUiStateMachine.model(
          props =
            LiteTrustedContactManagementProps(
              accountData = props.accountData,
              protectedCustomers = props.protectedCustomers,
              actions = props.socRecTrustedContactActions,
              acceptInvite = null,
              onExit = { uiState = State.ShowingAllSettingsList }
            )
        )

      is State.ShowingContactUs ->
        feedbackUiStateMachine.model(
          props = FeedbackUiProps(
            f8eEnvironment = props.accountData.account.config.f8eEnvironment,
            accountId = props.accountData.account.accountId,
            onBack = { uiState = State.ShowingAllSettingsList }
          )
        )

      is State.ShowingHelpCenter ->
        helpCenterUiStateMachine.model(
          props =
            HelpCenterUiProps(
              onBack = { uiState = State.ShowingAllSettingsList }
            )
        )

      is State.ShowingDebugMenu ->
        debugMenuStateMachine.model(
          props = DebugMenuProps(
            accountData = props.accountData,
            onClose = { uiState = State.ShowingAllSettingsList }
          )
        )
    }
  }

  @Composable
  private fun ShowingAllSettingsListScreen(
    props: LiteSettingsHomeUiProps,
    setState: (State) -> Unit,
  ): ScreenModel {
    var alertModel: ButtonAlertModel? by remember { mutableStateOf(null) }

    return ScreenModel(
      body =
        settingsListUiStateMachine.model(
          props =
            SettingsListUiProps(
              onBack = props.onBack,
              f8eEnvironment = props.accountData.account.config.f8eEnvironment,
              supportedRows = setOfNotNull(
                SettingsListUiProps.SettingsListRow.CurrencyPreference {
                  setState(State.ShowingCurrencyPreferenceSettings)
                },
                SettingsListUiProps.SettingsListRow.ContactUs {
                  setState(State.ShowingContactUs)
                },
                SettingsListUiProps.SettingsListRow.HelpCenter {
                  setState(State.ShowingHelpCenter)
                },
                SettingsListUiProps.SettingsListRow.TrustedContacts {
                  setState(State.ShowingTrustedContactsManagement)
                },
                SettingsListUiProps.SettingsListRow.DebugMenu {
                  setState(State.ShowingDebugMenu)
                }.takeIf { appVariant.showDebugMenu }
              ),
              onShowAlert = { alertModel = it },
              onDismissAlert = { alertModel = null }
            )
        ),
      statusBannerModel = props.homeStatusBannerModel,
      alertModel = alertModel
    )
  }
}

private sealed interface State {
  data object ShowingAllSettingsList : State

  data object ShowingCurrencyPreferenceSettings : State

  data object ShowingTrustedContactsManagement : State

  data object ShowingContactUs : State

  data object ShowingHelpCenter : State

  data object ShowingDebugMenu : State
}
