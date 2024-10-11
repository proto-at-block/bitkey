package build.wallet.statemachine.partnerships.sell

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.SellEventTrackerScreenId
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.ExactAmount
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.factor.SigningFactor
import build.wallet.bitkey.keybox.Keybox
import build.wallet.money.BitcoinMoney
import build.wallet.money.exchange.ExchangeRate
import build.wallet.money.exchange.ExchangeRateService
import build.wallet.partnerships.PartnerInfo
import build.wallet.partnerships.PartnershipTransaction
import build.wallet.partnerships.PartnershipTransactionId
import build.wallet.partnerships.PartnershipTransactionsStatusRepository
import build.wallet.statemachine.core.*
import build.wallet.statemachine.partnerships.PartnershipsSegment
import build.wallet.statemachine.partnerships.sell.ConfirmationState.*
import build.wallet.statemachine.send.TransferConfirmationScreenVariant
import build.wallet.statemachine.send.TransferConfirmationUiProps
import build.wallet.statemachine.send.TransferConfirmationUiStateMachine
import build.wallet.statemachine.send.fee.FeeSelectionUiProps
import build.wallet.statemachine.send.fee.FeeSelectionUiStateMachineImpl
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList

class PartnershipsSellConfirmationUiStateMachineImpl(
  private val transferConfirmationUiStateMachine: TransferConfirmationUiStateMachine,
  private val feeSelectionUiStateMachineImpl: FeeSelectionUiStateMachineImpl,
  private val partnershipsRepository: PartnershipTransactionsStatusRepository,
  private val exchangeRateService: ExchangeRateService,
) : PartnershipsSellConfirmationUiStateMachine {
  @Composable
  override fun model(props: PartnershipsSellConfirmationProps): ScreenModel {
    var state: ConfirmationState by remember {
      mutableStateOf(LoadingTransactionDetails)
    }

    var partnerInfo: PartnerInfo? by remember {
      mutableStateOf(null)
    }

    val exchangeRates: ImmutableList<ExchangeRate> by remember {
      mutableStateOf(exchangeRateService.exchangeRates.value.toImmutableList())
    }

    return when (val currentState = state) {
      LoadingTransactionDetails -> {
        LoadingTransactionDetailsModel(
          keybox = props.account.keybox,
          confirmedPartnerSale = props.confirmedPartnerSale,
          onLoadFailed = { errorData ->
            when (errorData.cause) {
              is TransactionError.NotFound -> {
                // If the transaction isn't found, assume they closed the web flow early
                props.onBack()
              }
              else -> {
                state = LoadingTransactionDetailsFailed(errorData)
              }
            }
          },
          onLoaded = { transaction ->
            val sellWalletAddress = transaction.sellWalletAddress
            val cryptoAmount = transaction.cryptoAmount

            state = if (sellWalletAddress != null && cryptoAmount != null) {
              partnerInfo = transaction.partnerInfo
              LoadingTransactionFees(
                partnerName = transaction.partnerInfo.name,
                sellWalletAddress = sellWalletAddress,
                cryptoAmount = cryptoAmount
              )
            } else {
              LoadingTransactionDetailsFailed(
                ErrorData(
                  segment = PartnershipsSegment.Sell.LoadTransactionDetails,
                  actionDescription = "Incomplete transaction details",
                  cause = TransactionError.InvalidTransactionDetails
                )
              )
            }
          },
          onBack = props.onBack
        )
      }

      is LoadingTransactionFees -> {
        LoadingTransactionFeesModel(
          recipientAddress = BitcoinAddress(currentState.sellWalletAddress),
          sendAmount = ExactAmount(BitcoinMoney.btc(currentState.cryptoAmount)),
          exchangeRates = exchangeRates,
          onLoaded = { _, fees ->
            state = LoadedSellConfirmation(
              fullAccount = props.account,
              partnerName = currentState.partnerName,
              sellWalletAddress = currentState.sellWalletAddress,
              cryptoAmount = currentState.cryptoAmount,
              fees = fees
            )
          },
          onBack = props.onBack
        )
      }

      is LoadedSellConfirmation -> {
        transferConfirmationUiStateMachine.model(
          TransferConfirmationUiProps(
            variant = TransferConfirmationScreenVariant.Sell(currentState.partnerName),
            selectedPriority = FASTEST,
            account = currentState.fullAccount,
            recipientAddress = BitcoinAddress(currentState.sellWalletAddress),
            sendAmount = ExactAmount(BitcoinMoney.btc(currentState.cryptoAmount)),
            requiredSigner = SigningFactor.Hardware,
            spendingLimit = null,
            fees = currentState.fees,
            exchangeRates = exchangeRates,
            onTransferInitiated = { _, _ ->
              props.onDone(partnerInfo)
            },
            onTransferFailed = {
              state = TransferFailed
            },
            onBack = props.onBack,
            onExit = props.onBack
          )
        )
      }

      is LoadingTransactionDetailsFailed -> {
        LoadingFailed(
          errorData = currentState.errorData,
          onBack = props.onBack
        )
      }

      TransferFailed -> {
        ErrorFormBodyModel(
          title = "Transfer failed",
          subline = "We were unable to complete the transfer. Please try again.",
          primaryButton =
            ButtonDataModel(
              text = "Dismiss",
              onClick = props.onBack
            ),
          onBack = props.onBack,
          eventTrackerScreenId = SellEventTrackerScreenId.SELL_TRANSFER_FAILED,
          errorData = ErrorData(
            segment = PartnershipsSegment.Sell.TransferConfirmation,
            actionDescription = "Transfer failed",
            cause = TransactionError.TransferFailed
          )
        ).asModalScreen()
      }
    }
  }

  @Composable
  private fun LoadingTransactionFeesModel(
    recipientAddress: BitcoinAddress,
    sendAmount: BitcoinTransactionSendAmount,
    exchangeRates: ImmutableList<ExchangeRate>,
    onLoaded: (
      EstimatedTransactionPriority,
      ImmutableMap<EstimatedTransactionPriority, Fee>,
    ) -> Unit,
    onBack: () -> Unit,
  ): ScreenModel {
    return feeSelectionUiStateMachineImpl.model(
      FeeSelectionUiProps(
        recipientAddress = recipientAddress,
        sendAmount = sendAmount,
        exchangeRates = exchangeRates,
        preselectedPriority = FASTEST,
        onBack = onBack,
        onContinue = onLoaded
      )
    ).asRootScreen()
  }

  @Composable
  private fun LoadingTransactionDetailsModel(
    keybox: Keybox,
    confirmedPartnerSale: ConfirmedPartnerSale,
    onLoadFailed: (ErrorData) -> Unit,
    onLoaded: (PartnershipTransaction) -> Unit,
    onBack: () -> Unit,
  ): ScreenModel {
    LaunchedEffect("load-transaction") {
      val transactionId = confirmedPartnerSale.partnerTransactionId
      if (transactionId == null) {
        onLoadFailed(
          ErrorData(
            segment = PartnershipsSegment.Sell.LoadTransactionDetails,
            actionDescription = "Missing transaction ID",
            cause = TransactionError.InvalidTransactionDetails
          )
        )
        return@LaunchedEffect
      }

      partnershipsRepository.syncTransaction(
        fullAccountId = keybox.fullAccountId,
        f8eEnvironment = keybox.config.f8eEnvironment,
        transactionId = transactionId
      )
        .onSuccess { transaction ->
          if (transaction != null) {
            onLoaded(transaction)
          } else {
            onLoadFailed(
              ErrorData(
                segment = PartnershipsSegment.Sell.LoadTransactionDetails,
                actionDescription = "Transaction not found",
                cause = TransactionError.NotFound(transactionId)
              )
            )
          }
        }
        .onFailure { exception ->
          onLoadFailed(
            ErrorData(
              segment = PartnershipsSegment.Sell.LoadTransactionDetails,
              actionDescription = "Failed to sync transaction details",
              cause = TransactionError.SyncFailed(exception)
            )
          )
        }
    }

    return LoadingBodyModel(
      onBack = onBack,
      id = SellEventTrackerScreenId.SELL_LOADING_TRANSACTION_DETAILS
    ).asRootScreen()
  }

  @Composable
  private fun LoadingFailed(
    errorData: ErrorData,
    onBack: () -> Unit,
  ): ScreenModel {
    return ErrorFormBodyModel(
      title = "Couldn't load transaction details",
      subline = "We were unable to load the transaction details. Please try again.",
      primaryButton =
        ButtonDataModel(
          text = "Dismiss",
          onClick = onBack
        ),
      onBack = onBack,
      eventTrackerScreenId = SellEventTrackerScreenId.SELL_TRANSACTION_DETAILS_ERROR,
      errorData = errorData
    ).asModalScreen()
  }
}

private sealed class TransactionError(
  message: String,
  cause: Throwable? = null,
) : Exception(message, cause) {
  data class NotFound(val transactionId: PartnershipTransactionId) :
    TransactionError("Transaction not found: $transactionId")

  data class SyncFailed(val originalException: Throwable) :
    TransactionError("Failed to sync transaction details", originalException)

  data object TransferFailed : TransactionError("Transfer failed")

  data object InvalidTransactionDetails : TransactionError("Invalid transaction details")
}

private sealed interface ConfirmationState {
  /**
   * Loading the transaction details
   */
  data object LoadingTransactionDetails : ConfirmationState

  /**
   * Loading the transaction fees
   *
   * @property partnerName - the partner name
   * @property sellWalletAddress - the sell wallet address of the partner
   * @property cryptoAmount - the crypto amount to be sent
   */
  data class LoadingTransactionFees(
    val partnerName: String,
    val sellWalletAddress: String,
    val cryptoAmount: Double,
  ) : ConfirmationState

  /**
   * The transaction details and fees have been loaded
   *
   * @property fullAccount - the account associated with the transfer
   * @property partnerName - the partner name
   * @property sellWalletAddress - the sell wallet address of the partner
   * @property cryptoAmount - the crypto amount to be sent
   * @property fees - the fees associated with the transfer
   */
  data class LoadedSellConfirmation(
    val fullAccount: FullAccount,
    val partnerName: String,
    val sellWalletAddress: String,
    val cryptoAmount: Double,
    val fees: ImmutableMap<EstimatedTransactionPriority, Fee>,
  ) : ConfirmationState

  /**
   * Loading the transaction details failed
   *
   * @property errorData - the error data associated with the failure
   */
  data class LoadingTransactionDetailsFailed(
    val errorData: ErrorData,
  ) : ConfirmationState

  /**
   * The transfer failed
   */
  data object TransferFailed : ConfirmationState
}
