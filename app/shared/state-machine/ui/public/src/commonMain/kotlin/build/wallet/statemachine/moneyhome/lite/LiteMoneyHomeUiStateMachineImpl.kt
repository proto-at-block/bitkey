package build.wallet.statemachine.moneyhome.lite

import androidx.compose.runtime.*
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.recovery.socrec.SocRecService
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.moneyhome.MoneyHomeButtonsModel
import build.wallet.statemachine.recovery.socrec.help.HelpingWithRecoveryUiProps
import build.wallet.statemachine.recovery.socrec.help.HelpingWithRecoveryUiStateMachine
import build.wallet.statemachine.recovery.socrec.view.ViewingProtectedCustomerProps
import build.wallet.statemachine.recovery.socrec.view.ViewingProtectedCustomerUiStateMachine
import kotlinx.collections.immutable.toImmutableList

@BitkeyInject(ActivityScope::class)
class LiteMoneyHomeUiStateMachineImpl(
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val viewingProtectedCustomerUiStateMachine: ViewingProtectedCustomerUiStateMachine,
  private val helpingWithRecoveryUiStateMachine: HelpingWithRecoveryUiStateMachine,
  private val socRecService: SocRecService,
) : LiteMoneyHomeUiStateMachine {
  @Composable
  override fun model(props: LiteMoneyHomeUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(State.ViewingMoneyHome) }

    val protectedCustomers =
      socRecService.socRecRelationships.collectAsState().value?.protectedCustomers.orEmpty()

    val viewingMoneyHomeScreenModel =
      ScreenModel(
        body = LiteMoneyHomeBodyModel(
          onSettings = props.onSettings,
          buttonModel = MoneyHomeButtonsModel.SingleButtonModel(
            onSetUpBitkeyDevice = { props.accountData.onUpgradeAccount() }
          ),
          protectedCustomers = protectedCustomers.toImmutableList(),
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
              account = props.accountData.account,
              screenModel = viewingMoneyHomeScreenModel,
              protectedCustomer = currentState.protectedCustomer,
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
