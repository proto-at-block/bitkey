package build.wallet.statemachine.settings.lite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.core.ScreenModel
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
import build.wallet.ui.model.alert.AlertModel

class LiteSettingsHomeUiStateMachineImpl(
  private val currencyPreferenceUiStateMachine: CurrencyPreferenceUiStateMachine,
  private val feedbackUiStateMachine: FeedbackUiStateMachine,
  private val helpCenterUiStateMachine: HelpCenterUiStateMachine,
  private val liteTrustedContactManagementUiStateMachine:
    LiteTrustedContactManagementUiStateMachine,
  private val settingsListUiStateMachine: SettingsListUiStateMachine,
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
              btcDisplayAmount = BitcoinMoney.zero(),
              currencyPreferenceData = props.currencyPreferenceData,
              onDone = null
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
    }
  }

  @Composable
  private fun ShowingAllSettingsListScreen(
    props: LiteSettingsHomeUiProps,
    setState: (State) -> Unit,
  ): ScreenModel {
    var alertModel: AlertModel? by remember { mutableStateOf(null) }

    return ScreenModel(
      body =
        settingsListUiStateMachine.model(
          props =
            SettingsListUiProps(
              onBack = props.onBack,
              f8eEnvironment = props.accountData.account.config.f8eEnvironment,
              supportedRows =
                setOf(
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
                  }
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
}
