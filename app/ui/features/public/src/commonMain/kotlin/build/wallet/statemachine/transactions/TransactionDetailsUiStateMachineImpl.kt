package build.wallet.statemachine.transactions

import androidx.compose.runtime.*
import build.wallet.activity.Transaction
import build.wallet.activity.TransactionsActivityService
import build.wallet.activity.onChainDetails
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action.ACTION_APP_ATTEMPT_SPEED_UP_TRANSACTION
import build.wallet.bdk.bindings.BdkError
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.explorer.BitcoinExplorer
import build.wallet.bitcoin.explorer.BitcoinExplorerType.Mempool
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimator
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.*
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Confirmed
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.*
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.bitcoin.wallet.SpendingWallet.PsbtConstructionMethod.BumpFee
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.FeeBumpIsAvailableFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logFailure
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.money.formatter.amountDisplayText
import build.wallet.money.orZero
import build.wallet.partnerships.PartnershipTransaction
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data
import build.wallet.statemachine.data.money.convertedOrNull
import build.wallet.statemachine.data.money.convertedOrZero
import build.wallet.statemachine.send.fee.FeeSelectionEventTrackerScreenId
import build.wallet.statemachine.transactions.TransactionDetailsUiStateMachineImpl.FeeLoadingError.TransactionMissingRecipientAddress
import build.wallet.statemachine.transactions.TransactionDetailsUiStateMachineImpl.UiState.*
import build.wallet.time.DateTimeFormatter
import build.wallet.time.DurationFormatter
import build.wallet.time.TimeZoneProvider
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.icon.*
import com.github.michaelbull.result.getOrElse
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime

@BitkeyInject(ActivityScope::class)
class TransactionDetailsUiStateMachineImpl(
  private val bitcoinExplorer: BitcoinExplorer,
  private val timeZoneProvider: TimeZoneProvider,
  private val dateTimeFormatter: DateTimeFormatter,
  private val currencyConverter: CurrencyConverter,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val clock: Clock,
  private val durationFormatter: DurationFormatter,
  private val eventTracker: EventTracker,
  private val bitcoinTransactionBumpabilityChecker: BitcoinTransactionBumpabilityChecker,
  private val feeBumpEnabled: FeeBumpIsAvailableFeatureFlag,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val feeBumpConfirmationUiStateMachine: FeeBumpConfirmationUiStateMachine,
  private val feeRateEstimator: BitcoinFeeRateEstimator,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val bitcoinWalletService: BitcoinWalletService,
  private val transactionsActivityService: TransactionsActivityService,
) : TransactionDetailsUiStateMachine {
  @Composable
  @Suppress("CyclomaticComplexMethod")
  override fun model(props: TransactionDetailsUiProps): ScreenModel {
    var uiState: UiState by remember {
      mutableStateOf(ShowingTransactionDetailUiState(props.transaction))
    }

    val transaction by remember(props.transaction.id) {
      transactionsActivityService.transactionById(props.transaction.id)
        .filterNotNull()
        .onEach {
          if (uiState is ShowingTransactionDetailUiState) {
            uiState = ShowingTransactionDetailUiState(it)
          }
        }
    }.collectAsState(props.transaction)

    return when (val state = uiState) {
      is SpeedingUpTransactionUiState ->
        feeBumpConfirmationUiStateMachine.model(
          FeeBumpConfirmationProps(
            account = props.account as FullAccount,
            speedUpTransactionDetails = state.speedUpTransactionDetails,
            onExit = { props.onClose() },
            psbt = state.psbt,
            newFeeRate = state.newFeeRate
          )
        )

      is ShowingTransactionDetailUiState -> {
        var isShowingEducationSheet by remember { mutableStateOf(false) }
        var isPreparingSpeedUp by remember { mutableStateOf(false) }

        if (isPreparingSpeedUp) {
          val bitcoinTransaction = transaction.onChainDetails()
          bitcoinTransaction?.let {
            LaunchedEffect("loading-rates-and-getting-wallet") {
              prepareTransactionSpeedUp(
                account = props.account,
                transaction = bitcoinTransaction,
                onInsufficientFunds = {
                  uiState = InsufficientFundsUiState
                },
                onFailedToPrepareData = {
                  uiState = FeeLoadingErrorUiState(TransactionMissingRecipientAddress)
                },
                onFeeRateTooLow = {
                  uiState = FeeRateTooLowUiState
                },
                onSuccessBumpingFee = { psbt, newFeeRate ->
                  when (val details = bitcoinTransaction.toSpeedUpTransactionDetails()) {
                    null -> FeeLoadingErrorUiState(TransactionMissingRecipientAddress)
                    else -> {
                      uiState = SpeedingUpTransactionUiState(
                        psbt = psbt,
                        newFeeRate = newFeeRate,
                        speedUpTransactionDetails = details
                      )
                    }
                  }
                }
              )
            }
          }
        }

        val onSpeedUpTransaction: () -> Unit = remember {
          {
            val speedUpTransactionDetails = when (val tx = transaction) {
              is Transaction.BitcoinWalletTransaction -> tx.details.toSpeedUpTransactionDetails()
              is Transaction.PartnershipTransaction -> tx.bitcoinTransaction?.toSpeedUpTransactionDetails()
            }
            eventTracker.track(ACTION_APP_ATTEMPT_SPEED_UP_TRANSACTION)
            when (speedUpTransactionDetails) {
              null -> FeeLoadingErrorUiState(TransactionMissingRecipientAddress)
              else -> {
                isPreparingSpeedUp = true
              }
            }
          }
        }

        val transactionUrl =
          transaction.transactionUrl(bitcoinNetworkType = props.account.config.bitcoinNetworkType)

        val transactionDetailModel = bitcoinTransactionDetailModel(
          transaction = transaction,
          isLoading = isPreparingSpeedUp,
          onViewSpeedUpEducation = {
            isShowingEducationSheet = true
          },
          viewTransactionText = transactionUrl?.buttonText,
          onViewTransaction = {
            transactionUrl?.url?.let {
              inAppBrowserNavigator.open(
                url = it,
                onClose = {}
              )
            }
          },
          onClose = props.onClose,
          onSpeedUpTransaction = onSpeedUpTransaction
        ).asModalScreen()

        val transactionSpeedUpEducationModel = SheetModel(
          size = SheetSize.MIN40,
          onClosed = {
            isShowingEducationSheet = false
          },
          body = BitcoinTransactionSpeedUpEducationBodyModel(
            onSpeedUpTransaction = onSpeedUpTransaction,
            onClose = {
              isShowingEducationSheet = false
            }
          )
        )

        transactionDetailModel.body.asRootScreen(
          bottomSheetModel = transactionSpeedUpEducationModel.takeIf { isShowingEducationSheet }
        )
      }

      // TODO [W-5841]: refactor to use common error-handling state machine with FeeSelectionUiStateMachine
      is FeeLoadingErrorUiState -> ErrorFormBodyModel(
        title = "We couldn’t speed up this transaction",
        subline = "We are looking into this. Please try again later.",
        primaryButton = ButtonDataModel(
          text = "Go Back",
          onClick = { uiState = ShowingTransactionDetailUiState(transaction) }
        ),
        eventTrackerScreenId = FeeSelectionEventTrackerScreenId.FEE_ESTIMATION_INSUFFICIENT_FUNDS_ERROR_SCREEN
      ).asModalScreen()

      is InsufficientFundsUiState -> ErrorFormBodyModel(
        title = "We couldn’t speed up this transaction",
        subline = "There are not enough funds to speed up the transaction. Please add more funds and try again.",
        primaryButton = ButtonDataModel(
          text = "Go Back",
          onClick = { uiState = ShowingTransactionDetailUiState(transaction) }
        ),
        eventTrackerScreenId = null
      ).asModalScreen()

      FeeRateTooLowUiState -> ErrorFormBodyModel(
        title = "We couldn’t speed up this transaction",
        subline = "The current fee rate is too low. Please try again later.",
        primaryButton = ButtonDataModel(
          text = "Go Back",
          onClick = { uiState = ShowingTransactionDetailUiState(transaction) }
        ),
        eventTrackerScreenId = null
      ).asModalScreen()
    }
  }

  /**
   * A simple wrapper around the transaction url to open and the button text for the button that
   * should trigger opening the url. This is needed as we may open mempool for an onchain transaction
   * or redirect to a partner for a partnership transaction.
   */
  private data class TransactionUrl(
    val url: String,
    val buttonText: String,
  )

  private fun Transaction.transactionUrl(bitcoinNetworkType: BitcoinNetworkType) =
    when (this) {
      is Transaction.BitcoinWalletTransaction -> TransactionUrl(
        url = bitcoinExplorer.getTransactionUrl(
          txId = details.id,
          network = bitcoinNetworkType,
          explorerType = Mempool
        ),
        buttonText = "View transaction"
      )

      is Transaction.PartnershipTransaction -> details.partnerTransactionUrl?.let {
        TransactionUrl(
          url = it,
          buttonText = "View in ${details.partnerInfo.name}"
        )
      } ?: bitcoinTransaction?.let {
        TransactionUrl(
          url = bitcoinExplorer.getTransactionUrl(
            txId = it.id,
            network = bitcoinNetworkType,
            explorerType = Mempool
          ),
          buttonText = "View transaction"
        )
      }
    }

  // TODO: extract into a domain service, for example `SpeedUpBitcoinTransactionService`
  private suspend fun prepareTransactionSpeedUp(
    account: Account,
    transaction: BitcoinTransaction,
    onFailedToPrepareData: () -> Unit,
    onInsufficientFunds: () -> Unit,
    onFeeRateTooLow: () -> Unit,
    onSuccessBumpingFee: (psbt: Psbt, newFeeRate: FeeRate) -> Unit,
  ) {
    feeRateEstimator.estimatedFeeRateForTransaction(
      networkType = account.config.bitcoinNetworkType,
      estimatedTransactionPriority = FASTEST
    )
      .also { feeRate ->
        // For test accounts on Signet, we manually choose a fee rate that is 5 times the previous
        // one. This is particularly useful for QA when testing.
        val newFeeRate =
          if (account.config.isTestAccount && account.config.bitcoinNetworkType == SIGNET) {
            FeeRate(satsPerVByte = feeRate.satsPerVByte * 5)
          } else {
            feeRate
          }
        val constructionMethod = BumpFee(txid = transaction.id, feeRate = newFeeRate)

        val wallet = bitcoinWalletService.spendingWallet().value
        if (wallet == null) {
          onFailedToPrepareData()
          return
        }
        val psbt = wallet
          .createSignedPsbt(constructionMethod)
          .logFailure { "Unable to build fee bump psbt" }
          .getOrElse {
            when (it) {
              is BdkError.InsufficientFunds -> onInsufficientFunds()
              is BdkError.FeeRateTooLow -> onFeeRateTooLow()
              else -> onFailedToPrepareData()
            }
            return
          }

        onSuccessBumpingFee(psbt, newFeeRate)
      }
  }

  @Composable
  private fun partnershipTransactionFormContent(
    transaction: PartnershipTransaction,
  ): ImmutableList<FormMainContentModel> {
    val totalAmount = transaction.cryptoAmount?.let { BitcoinMoney.btc(it) }

    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()

    val totalFiatAmount = totalAmount?.let {
      convertedOrNull(
        converter = currencyConverter,
        fromAmount = totalAmount,
        toCurrency = fiatCurrency,
        atTime = transaction.created
      ) as FiatMoney?
    }

    val totalAmountTexts = totalAmount?.let {
      moneyDisplayFormatter.amountDisplayText(
        bitcoinAmount = totalAmount,
        fiatAmount = totalFiatAmount
      )
    }

    return immutableListOfNotNull(
      submittedTransactionStepper,
      FormMainContentModel.Divider,
      DataList(
        items = immutableListOf(
          Data(
            title = "Submitted",
            sideText = dateTimeFormatter.shortDateWithTime(
              localDateTime = transaction.created.toLocalDateTime(timeZoneProvider.current())
            )
          )
        )
      ),
      totalAmountTexts?.let {
        DataList(
          items = immutableListOf(
            Data(
              title = "Amount",
              sideText = totalAmountTexts.primaryAmountText,
              secondarySideText = totalAmountTexts.secondaryAmountText
            )
          )
        )
      }
    )
  }

  @Composable
  private fun bitcoinTransactionFormContent(
    transaction: BitcoinTransaction,
    onViewSpeedUpEducation: () -> Unit,
  ): ImmutableList<FormMainContentModel> {
    val atTime = when (transaction.transactionType) {
      Incoming, UtxoConsolidation -> transaction.confirmationTime()
      Outgoing -> transaction.broadcastTime ?: transaction.confirmationTime()
    }

    val totalAmount = when (transaction.transactionType) {
      Incoming, UtxoConsolidation -> transaction.subtotal
      Outgoing -> transaction.total
    }

    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()

    val totalFiatAmount = convertedOrZero(
      converter = currencyConverter,
      fromAmount = totalAmount,
      toCurrency = fiatCurrency,
      atTime = atTime
    ) as FiatMoney

    val totalAmountTexts = moneyDisplayFormatter.amountDisplayText(
      bitcoinAmount = totalAmount,
      fiatAmount = totalFiatAmount
    )

    val subtotalFiatAmount = convertedOrNull(
      converter = currencyConverter,
      fromAmount = transaction.subtotal,
      toCurrency = fiatCurrency,
      atTime = atTime
    ) as FiatMoney?
    val subtotalAmountTexts = moneyDisplayFormatter.amountDisplayText(
      bitcoinAmount = transaction.subtotal,
      fiatAmount = subtotalFiatAmount
    )

    val transactionFee = transaction.fee.orZero()
    val transactionFeeFiat = convertedOrNull(
      converter = currencyConverter,
      fromAmount = transactionFee,
      toCurrency = fiatCurrency,
      atTime = atTime
    ) as FiatMoney?

    val transactionFeeAmountTexts = moneyDisplayFormatter.amountDisplayText(
      bitcoinAmount = transactionFee,
      fiatAmount = transactionFeeFiat
    )

    val confirmationData = when (val status = transaction.confirmationStatus) {
      is Confirmed -> Data(
        title = "Confirmed",
        sideText = dateTimeFormatter.shortDateWithTime(
          localDateTime = status.blockTime.timestamp.toLocalDateTime(timeZoneProvider.current())
        )
      )

      is Pending -> pendingDataListItem(
        estimatedConfirmationTime = transaction.estimatedConfirmationTime,
        onViewSpeedUpEducation = onViewSpeedUpEducation
      )
    }

    val transactionDetails = DataList(
      items = when (transaction.transactionType) {
        Incoming -> immutableListOf(
          Data(
            title = "Amount",
            sideText = subtotalAmountTexts.primaryAmountText,
            secondarySideText = subtotalAmountTexts.secondaryAmountText
          )
        )

        UtxoConsolidation -> {
          immutableListOf(
            Data(
              title = "UTXOs consolidated",
              sideText = "${transaction.inputs.size} → 1"
            ),
            Data(
              title = "Consolidation cost",
              sideText = transactionFeeAmountTexts.primaryAmountText,
              secondarySideText = transactionFeeAmountTexts.secondaryAmountText
            )
          )
        }

        Outgoing ->
          immutableListOf(
            Data(
              title = "Amount",
              sideText = subtotalAmountTexts.primaryAmountText,
              secondarySideText = subtotalAmountTexts.secondaryAmountText
            ),
            Data(
              title = "Network fees",
              sideText = transactionFeeAmountTexts.primaryAmountText,
              secondarySideText = transactionFeeAmountTexts.secondaryAmountText
            )
          )
      },
      total = run {
        val shouldDisplayTotal = transaction.transactionType == Outgoing
        Data(
          title = "Total",
          sideText = totalAmountTexts.primaryAmountText,
          sideTextType = Data.SideTextType.BODY2BOLD,
          secondarySideText = totalAmountTexts.secondaryAmountText
        ).takeIf { shouldDisplayTotal }
      }
    )

    val stepper = when (transaction.confirmationStatus) {
      Pending -> processingTransactionStepper
      is Confirmed -> completeTransactionStepper
    }

    return immutableListOfNotNull(
      stepper,
      FormMainContentModel.Divider,
      confirmationData?.let { DataList(items = immutableListOf(it)) },
      transactionDetails
    )
  }

  private fun pendingDataListItem(
    estimatedConfirmationTime: Instant?,
    onViewSpeedUpEducation: () -> Unit,
  ): Data? {
    if (!feeBumpEnabled.flagValue().value.value || estimatedConfirmationTime == null) {
      return null
    }

    return estimatedConfirmationTime.let { confirmationTime ->
      val currentTime = clock.now()
      if (confirmationTime < currentTime) {
        Data(
          title = "Should have arrived by",
          sideText =
            dateTimeFormatter.shortDateWithTime(
              localDateTime = confirmationTime.toLocalDateTime(timeZoneProvider.current())
            ),
          sideTextTreatment = Data.SideTextTreatment.STRIKETHROUGH,
          sideTextType = Data.SideTextType.REGULAR,
          secondarySideText = "${
            durationFormatter.formatWithAlphabet(
              currentTime - confirmationTime
            )
          } late",
          secondarySideTextType = Data.SideTextType.BOLD,
          secondarySideTextTreatment = Data.SideTextTreatment.WARNING,
          explainer =
            Data.Explainer(
              title = "Speed up transaction?",
              subtitle = "You can speed up this transaction by increasing the network fee.",
              iconButton = IconButtonModel(
                iconModel = IconModel(
                  icon = Icon.SmallIconInformationFilled,
                  iconSize = IconSize.XSmall,
                  iconBackgroundType = IconBackgroundType.Circle(
                    circleSize = IconSize.XSmall
                  ),
                  iconTint = IconTint.Foreground,
                  iconOpacity = 0.20f
                ),
                onClick = StandardClick(onViewSpeedUpEducation)
              )
            )
        )
      } else {
        Data(
          title = "Arrival time",
          sideText =
            dateTimeFormatter.shortDateWithTime(
              localDateTime = confirmationTime.toLocalDateTime(timeZoneProvider.current())
            )
        )
      }
    }
  }

  @Composable
  private fun bitcoinTransactionDetailModel(
    transaction: Transaction,
    isLoading: Boolean,
    viewTransactionText: String?,
    onViewTransaction: () -> Unit,
    onClose: () -> Unit,
    onSpeedUpTransaction: () -> Unit,
    onViewSpeedUpEducation: () -> Unit,
  ): TransactionDetailModel {
    val transactionsData by remember { bitcoinWalletService.transactionsData() }.collectAsState()

    val allUtxos = remember(transactionsData) { transactionsData?.utxos?.all.orEmpty() }

    val feeBumpEnabled by remember {
      val onchainDetails = transaction.onChainDetails()
      mutableStateOf(
        onchainDetails != null &&
          feeBumpEnabled.isEnabled() && bitcoinTransactionBumpabilityChecker.isBumpable(
            transaction = onchainDetails,
            walletUnspentOutputs = allUtxos.toImmutableList()
          )
      )
    }
    return TransactionDetailModel(
      feeBumpEnabled = feeBumpEnabled,
      formHeaderModel = when (transaction.onChainDetails()?.confirmationStatus ?: Pending) {
        is Pending -> pendingFormHeaderModel(
          // Some transactions may not have an estimate confirmation time, if they don't, we don't
          // attempt to show "Transaction delayed", just "Transaction pending".
          isLate = transaction.onChainDetails()?.isLate(clock) == true,
          transaction = transaction
        )

        is Confirmed -> confirmedFormHeaderModel(
          transaction = transaction
        )
      },
      isLoading = isLoading,
      viewTransactionText = viewTransactionText,
      onViewTransaction = onViewTransaction,
      onClose = onClose,
      onSpeedUpTransaction = onSpeedUpTransaction,
      content = when (transaction) {
        is Transaction.BitcoinWalletTransaction -> bitcoinTransactionFormContent(
          transaction = transaction.details,
          onViewSpeedUpEducation = onViewSpeedUpEducation
        )

        is Transaction.PartnershipTransaction -> if (transaction.bitcoinTransaction != null) {
          bitcoinTransactionFormContent(
            transaction = requireNotNull(transaction.bitcoinTransaction),
            onViewSpeedUpEducation = onViewSpeedUpEducation
          )
        } else {
          partnershipTransactionFormContent(
            transaction = transaction.details
          )
        }
      }
    )
  }

  private sealed interface UiState {
    /**
     * Customer is viewing transaction details.
     */
    data class ShowingTransactionDetailUiState(val transaction: Transaction) : UiState

    /**
     * Customer is showing speed up confirmation flow.
     */
    data class SpeedingUpTransactionUiState(
      val psbt: Psbt,
      val newFeeRate: FeeRate,
      val speedUpTransactionDetails: SpeedUpTransactionDetails,
    ) : UiState

    /**
     * User currently does not have enough funds to fee bump the transaction.
     */
    data object InsufficientFundsUiState : UiState

    /**
     * Fee rates are currently too low to fee bump the transaction.
     */
    data object FeeRateTooLowUiState : UiState

    /**
     * We failed to construct a fee estimation required to fee bump a transaction.
     */
    data class FeeLoadingErrorUiState(
      val feeLoadingError: FeeLoadingError,
    ) : UiState
  }

  /**
   * Describes different ways loading fees can fail when speeding up a transaction.
   */
  sealed class FeeLoadingError : Error() {
    /**
     * The transaction that was loaded to props was missing a recipient address.
     *
     * We do not expect this to happen, but handle it just in case.
     */
    data object TransactionMissingRecipientAddress : FeeLoadingError()
  }
}
