package build.wallet.statemachine.partnerships.sell

import androidx.compose.runtime.*
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.partnerships.PartnerInfo
import build.wallet.partnerships.PartnerRedirectionMethod
import build.wallet.partnerships.PartnershipEvent
import build.wallet.partnerships.PartnershipTransaction
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.partnerships.sell.SellState.*

class PartnershipsSellUiStateMachineImpl(
  private val partnershipsSellOptionsUiStateMachine: PartnershipsSellOptionsUiStateMachine,
  private val partnershipsSellConfirmationUiStateMachine:
    PartnershipsSellConfirmationUiStateMachine,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
) : PartnershipsSellUiStateMachine {
  @Composable
  override fun model(props: PartnershipsSellUiProps): ScreenModel {
    var state: SellState by remember {
      props.confirmedSale?.let { sale ->
        mutableStateOf(SellConfirmation(sale))
      } ?: mutableStateOf(ListSellPartners)
    }

    return when (val currentState = state) {
      is ListSellPartners -> {
        partnershipsSellOptionsUiStateMachine.model(
          PartnershipsSellOptionsUiProps(
            keybox = props.account.keybox,
            onBack = props.onBack,
            onPartnerRedirected = { method, transaction ->
              handlePartnerRedirected(
                method = method,
                transaction = transaction,
                setState = {
                  state = it
                }
              )
            }
          )
        )
      }

      is ShowingSellRedirect -> {
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = currentState.urlString,
              onClose = currentState.onClose
            )
          }
        ).asModalScreen()
      }

      is SellConfirmation -> {
        partnershipsSellConfirmationUiStateMachine.model(
          PartnershipsSellConfirmationProps(
            account = props.account,
            confirmedPartnerSale = currentState.confirmedPartnerSale,
            onBack = {
              state = ListSellPartners
            },
            exchangeRates = emptyImmutableList(),
            onDone = { partnerInfo ->
              state = ShowingSellSuccess(partnerInfo)
            }
          )
        )
      }

      is TrackSell -> {
        TODO("Show external tracking link in webview")
      }

      is ShowingSellSuccess -> SellBitcoinSuccessBodyModel(
        partnerInfo = currentState.partnerInfo,
        onBack = props.onBack
      ).asModalScreen()
    }
  }

  private fun handlePartnerRedirected(
    method: PartnerRedirectionMethod,
    transaction: PartnershipTransaction,
    setState: (SellState) -> Unit,
  ) {
    when (method) {
      is PartnerRedirectionMethod.Web -> {
        setState(
          ShowingSellRedirect(
            urlString = method.urlString,
            onClose = {
              setState(
                SellConfirmation(
                  ConfirmedPartnerSale(
                    partner = transaction.partnerInfo.partnerId,
                    event = transaction.context?.let { PartnershipEvent(it) },
                    partnerTransactionId = transaction.id
                  )
                )
              )
            }
          )
        )
      }
      is PartnerRedirectionMethod.Deeplink ->
        TODO("PartnerRedirectionMethod.Deeplink not implemented")
    }
  }
}

sealed interface SellState {
  data object ListSellPartners : SellState

  /**
   * Indicates that we are displaying an in-app browser for a sell redirect.
   *
   * @param urlString - url to kick off the in-app browser with.
   * @param onClose - callback fired when browser closes
   */
  data class ShowingSellRedirect(
    val urlString: String,
    val onClose: () -> Unit,
  ) : SellState

  data class SellConfirmation(
    val confirmedPartnerSale: ConfirmedPartnerSale,
  ) : SellState

  data object TrackSell : SellState

  data class ShowingSellSuccess(val partnerInfo: PartnerInfo?) : SellState
}
