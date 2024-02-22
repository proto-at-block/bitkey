package build.wallet.statemachine.moneyhome.lite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.money.BitcoinMoney
import build.wallet.money.Money
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.recovery.socrec.help.HelpingWithRecoveryUiProps
import build.wallet.statemachine.recovery.socrec.help.HelpingWithRecoveryUiStateMachine
import build.wallet.statemachine.recovery.socrec.view.ViewingProtectedCustomerProps
import build.wallet.statemachine.recovery.socrec.view.ViewingProtectedCustomerUiStateMachine
import com.ionspin.kotlin.bignum.decimal.toBigDecimal

class LiteMoneyHomeUiStateMachineImpl(
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
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
            props = props,
            onViewProtectedCustomerDetail = {
              state = State.ViewingProtectedCustomerDetail(it)
            },
            onBuyOwnBitkeyClick = {
              state = State.ViewingBuyOwnBitkeyUrl
            },
            onSetUpBitkeyDeviceClick = {
              props.accountData.onUpgradeAccount()
            }
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

  @Composable
  private fun LiteMoneyHomeBodyModel(
    props: LiteMoneyHomeUiProps,
    onViewProtectedCustomerDetail: (ProtectedCustomer) -> Unit,
    onBuyOwnBitkeyClick: () -> Unit,
    onSetUpBitkeyDeviceClick: () -> Unit,
  ): BodyModel {
    // We use a placeholder amount of 0 just to demonstrate to
    // "lite" customers what the full experience looks like
    val zeroFiat = Money.money(props.fiatCurrency, 0.toBigDecimal())

    return LiteMoneyHomeBodyModel(
      onSettings = props.onSettings,
      primaryAmountString = moneyDisplayFormatter.format(zeroFiat),
      secondaryAmountString = moneyDisplayFormatter.format(BitcoinMoney.zero()),
      onSetUpBitkeyDevice = onSetUpBitkeyDeviceClick,
      protectedCustomers = props.protectedCustomers,
      onProtectedCustomerClick = onViewProtectedCustomerDetail,
      onBuyOwnBitkeyClick = onBuyOwnBitkeyClick,
      onAcceptInviteClick = props.onAcceptInvite
    )
  }
}

private sealed interface State {
  data object ViewingMoneyHome : State

  data class ViewingProtectedCustomerDetail(val protectedCustomer: ProtectedCustomer) : State

  data object ViewingBuyOwnBitkeyUrl : State

  data class HelpingWithRecovery(val protectedCustomer: ProtectedCustomer) : State
}
