package build.wallet.statemachine.settings.lite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.config.AppVariant
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.dev.DebugMenuProps
import build.wallet.statemachine.dev.DebugMenuStateMachine
import build.wallet.statemachine.money.currency.AppearancePreferenceProps
import build.wallet.statemachine.money.currency.AppearancePreferenceUiStateMachine
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

@BitkeyInject(ActivityScope::class)
class LiteSettingsHomeUiStateMachineImpl(
  private val appVariant: AppVariant,
  private val appearancePreferenceUiStateMachine: AppearancePreferenceUiStateMachine,
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
        appearancePreferenceUiStateMachine.model(
          props =
            AppearancePreferenceProps(
              onBack = { uiState = State.ShowingAllSettingsList }
            )
        )

      is State.ShowingTrustedContactsManagement ->
        liteTrustedContactManagementUiStateMachine.model(
          props = LiteTrustedContactManagementProps(
            account = props.account,
            acceptInvite = null,
            onExit = { uiState = State.ShowingAllSettingsList },
            onAccountUpgraded = props.onAccountUpgraded
          )
        )

      is State.ShowingContactUs ->
        feedbackUiStateMachine.model(
          props = FeedbackUiProps(
            f8eEnvironment = props.account.config.f8eEnvironment,
            accountId = props.account.accountId,
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
            onClose = { uiState = State.ShowingAllSettingsList },
            onAppDataDeleted = props.onAppDataDeleted
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
              f8eEnvironment = props.account.config.f8eEnvironment,
              supportedRows = setOfNotNull(
                SettingsListUiProps.SettingsListRow.AppearancePreference {
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
