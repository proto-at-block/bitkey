package build.wallet.statemachine.partnerships.expected

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.ExpectedTransactionTrackerScreenId.EXPECTED_TRANSACTION_NOTICE_LOADING
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.logging.logFailure
import build.wallet.partnerships.PartnershipEvent
import build.wallet.partnerships.PartnershipTransaction
import build.wallet.partnerships.PartnershipTransactionStatus
import build.wallet.partnerships.PartnershipTransactionsService
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.time.DateTimeFormatter
import build.wallet.time.Delayer
import build.wallet.time.withMinimumDelay
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

class ExpectedTransactionNoticeUiStateMachineImpl(
  private val partnershipTransactionsService: PartnershipTransactionsService,
  private val dateTimeFormatter: DateTimeFormatter,
  private val delayer: Delayer,
) : ExpectedTransactionNoticeUiStateMachine {
  @Composable
  override fun model(props: ExpectedTransactionNoticeProps): ScreenModel {
    var state by remember { mutableStateOf<State>(State.LoadingPartnershipDetails) }

    when (state) {
      is State.LoadingPartnershipDetails -> LaunchedEffect("load partnership details") {
        when (props.event) {
          PartnershipEvent.TransactionCreated -> {
            state = delayer.withMinimumDelay {
              props.partner?.let {
                partnershipTransactionsService
                  .updateRecentTransactionStatusIfExists(
                    partnerId = props.partner,
                    status = PartnershipTransactionStatus.PENDING
                  )
                  .logFailure { "Unable to fetch partner info for expected transaction screen" }
                  .get()
                  .let { State.TransactionDetails(transaction = it) }
              } ?: State.TransactionDetails(transaction = null)
            }
          }

          PartnershipEvent.WebFlowCompleted -> state = State.LoadingTransferDetails
          else -> {
            // Event types can be specified by deep link, so there is a possibility
            // that an unknown event type could be passed in.
            // Log error and safely exit flow so user is not stuck:
            log(LogLevel.Error) { "Unknown Partnership Event: ${props.event}" }
            props.onBack()
          }
        }
      }
      is State.LoadingTransferDetails -> LaunchedEffect("load transfer details") {
        if (props.partnerTransactionId == null) {
          // If no partner transaction ID was specified, we won't be able
          // to look up the transfer status. This state should not happen,
          // However, we will log it as an error and exit the screen safely.
          log(LogLevel.Error) { "No partner transaction ID specified. Exiting Notice Screen." }
          props.onBack()
        } else {
          delayer
            .withMinimumDelay {
              partnershipTransactionsService.syncTransaction(
                accountId = props.accountId,
                f8eEnvironment = props.f8eEnvironment,
                transactionId = props.partnerTransactionId
              )
            }
            .onSuccess { transaction ->
              if (transaction != null) {
                state = State.TransactionDetails(
                  transaction = transaction
                )
              } else {
                // User did not complete a transaction. Exit to home screen.
                log(LogLevel.Info) { "No partner transaction found. Exiting Notice Screen." }
                props.onBack()
              }
            }
            .logFailure { "Failed to fetch most recent transaction for Notice Screen" }
            .onFailure { error ->
              // If the fetch operation fails, we can't be sure a transaction was created.
              // Exit to the home screen safely to avoid user confusion.
              props.onBack()
            }
        }
      }
      else -> {}
    }

    return when (val currentState = state) {
      is State.LoadingPartnershipDetails,
      is State.LoadingTransferDetails,
      -> LoadingBodyModel(
        id = EXPECTED_TRANSACTION_NOTICE_LOADING
      )
      is State.TransactionDetails -> ExpectedTransactionNoticeModel(
        partnerInfo = currentState.transaction?.partnerInfo,
        transactionDate = dateTimeFormatter.shortDateWithTime(props.receiveTime),
        onViewInPartnerApp = props.onViewInPartnerApp,
        onBack = props.onBack
      )
    }.asModalFullScreen()
  }

  private sealed interface State {
    /**
     * Loading state for fetching details about the partner.
     *
     * This is necessary when the user is redirected from a partner app
     * into our app via a deep link after a successful transaction.
     */
    data object LoadingPartnershipDetails : State

    /**
     * Loading state for fetching transaction information from partners.
     *
     * This is necessary when the user completes a web partner's flow
     * in order to look up whether a transaction was created while in the
     * web browser, since there is no deeplink callback at the end of the
     * flow.
     */
    data object LoadingTransferDetails : State

    /**
     * Loaded state with all of the available information to show the
     * user about their completed transaction.
     */
    data class TransactionDetails(
      val transaction: PartnershipTransaction? = null,
    ) : State
  }
}
