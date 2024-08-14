package build.wallet.statemachine.moneyhome.lite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.moneyhome.MoneyHomeButtonsModel
import build.wallet.statemachine.recovery.socrec.help.HelpingWithRecoveryUiProps
import build.wallet.statemachine.recovery.socrec.help.HelpingWithRecoveryUiStateMachine
import build.wallet.statemachine.recovery.socrec.view.ViewingProtectedCustomerProps
import build.wallet.statemachine.recovery.socrec.view.ViewingProtectedCustomerUiStateMachine

class LiteMoneyHomeUiStateMachineImpl(
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val viewingProtectedCustomerUiStateMachine: ViewingProtectedCustomerUiStateMachine,
  private val helpingWithRecoveryUiStateMachine: HelpingWithRecoveryUiStateMachine,
) : LiteMoneyHomeUiStateMachine {
  @Composable
  override fun model(props: LiteMoneyHomeUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(State.ViewingMoneyHome) }

    val viewingMoneyHomeScreenModel =
      ScreenModel(
        body =
          LiteMoneyHomeBodyModel(
            onSettings = props.onSettings,
            buttonModel = MoneyHomeButtonsModel.SingleButtonModel(
              onSetUpBitkeyDevice = { props.accountData.onUpgradeAccount() }
            ),
            protectedCustomers = props.protectedCustomers,
            badgedSettingsIcon = false,
            onProtectedCustomerClick = {
              state = State.ViewingProtectedCustomerDetail(it)
            },
            onBuyOwnBitkeyClick = {
              state = State.ViewingBuyOwnBitkeyUrl
            },
            onAcceptInviteClick = props.onAcceptInvite
          ),
        statusBannerModel = props.homeStatusBannerModel
      )

    return when (val currentState = state) {
      is State.ViewingMoneyHome ->
        return viewingMoneyHomeScreenModel

      is State.ViewingProtectedCustomerDetail ->
        viewingProtectedCustomerUiStateMachine.model(
          props =
            ViewingProtectedCustomerProps(
              screenModel = viewingMoneyHomeScreenModel,
              protectedCustomer = currentState.protectedCustomer,
              onRemoveProtectedCustomer = {
                props.onRemoveRelationship(currentState.protectedCustomer)
              },
              onHelpWithRecovery = {
                state =
                  State.HelpingWithRecovery(
                    protectedCustomer = currentState.protectedCustomer
                  )
              },
              onExit = {
                state = State.ViewingMoneyHome
              }
            )
        )

      is State.ViewingBuyOwnBitkeyUrl ->
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = "https://bitkey.world/",
              onClose = {
                state = State.ViewingMoneyHome
              }
            )
          }
        ).asModalScreen()

      is State.HelpingWithRecovery ->
        helpingWithRecoveryUiStateMachine.model(
          props =
            HelpingWithRecoveryUiProps(
              account = props.accountData.account,
              protectedCustomer = currentState.protectedCustomer,
              onExit = {
                state = State.ViewingMoneyHome
              }
            )
        )
    }
  }
}

private sealed interface State {
  data object ViewingMoneyHome : State

  data class ViewingProtectedCustomerDetail(val protectedCustomer: ProtectedCustomer) : State

  data object ViewingBuyOwnBitkeyUrl : State

  data class HelpingWithRecovery(val protectedCustomer: ProtectedCustomer) : State
}
