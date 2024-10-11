package build.wallet.statemachine.send

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkError.InsufficientFunds
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.*
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitkey.factor.SigningFactor.F8e
import build.wallet.bitkey.factor.SigningFactor.Hardware
import build.wallet.ensureNotNull
import build.wallet.limit.MobilePayService
import build.wallet.limit.SpendingLimit
import build.wallet.logging.LogLevel.Error
import build.wallet.logging.log
import build.wallet.logging.logFailure
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.send.TransferConfirmationUiState.*
import build.wallet.statemachine.send.TransferConfirmationUiState.ErrorUiState.*
import build.wallet.statemachine.send.TransferConfirmationUiState.ViewingTransferConfirmationUiState.SheetState.*
import build.wallet.statemachine.send.fee.FeeOptionListProps
import build.wallet.statemachine.send.fee.FeeOptionListUiStateMachine
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Companion.BitkeyInteractionButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap

class TransferConfirmationUiStateMachineImpl(
  private val transactionDetailsCardUiStateMachine: TransactionDetailsCardUiStateMachine,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val transactionPriorityPreference: TransactionPriorityPreference,
  private val feeOptionListUiStateMachine: FeeOptionListUiStateMachine,
  private val transactionsService: TransactionsService,
  private val mobilePayService: MobilePayService,
) : TransferConfirmationUiStateMachine {
  @Composable
  override fun model(props: TransferConfirmationUiProps): ScreenModel {
    var uiState: TransferConfirmationUiState by remember {
      mutableStateOf(CreatingAppSignedPsbtUiState)
    }

    var selectedPriority: EstimatedTransactionPriority by remember {
      mutableStateOf(props.selectedPriority)
    }

    var appSignedPsbts: Map<EstimatedTransactionPriority, Psbt> by remember {
      mutableStateOf(persistentMapOf())
    }

    when (val state = uiState) {
      is BroadcastingTransactionUiState ->
        BroadcastingTransactionEffect(
          props = props,
          state = state,
          selectedPriority = selectedPriority,
          onBdkError = {
            uiState = ReceivedBdkErrorUiState
          }
        )
      is SigningWithServerUiState ->
        SigningWithServerEffect(
          state = state,
          onSignSuccess = { appAndServerSignedPsbt ->
            uiState =
              BroadcastingTransactionUiState(
                twoOfThreeSignedPsbt = appAndServerSignedPsbt
              )
          },
          onSignError = {
            uiState =
              ReceivedServerSigningErrorUiState(
                state.appSignedPsbt
              )
          }
        )
      else -> Unit
    }

    return when (val state = uiState) {
      is BroadcastingTransactionUiState, is SigningWithServerUiState ->
        LoadingBodyModel(
          message = "Initiating transfer...",
          onBack = {
            uiState = CreatingAppSignedPsbtUiState
          },
          id = SendEventTrackerScreenId.SEND_SIGNING_AND_BROADCASTING_LOADING,
          eventTrackerShouldTrack = false
        ).asModalScreen()
      is CreatingAppSignedPsbtUiState ->
        CreatingAppSignedPsbt(
          props = props,
          selectedPriority = selectedPriority,
          onAppSignSuccess = { psbts ->
            appSignedPsbts = psbts

            uiState =
              ViewingTransferConfirmationUiState(
                appSignedPsbt =
                  psbts[selectedPriority]
                    ?: error("This callback should not be invoked without selected priority, this shouldn’t happen")
              )
          },
          onAppSignError = {
            log(Error) { "Unable to sign PSBT" }
            uiState = ReceivedBdkErrorUiState
          },
          onPsbtCreateError = { error ->
            log(Error) { "Unable to create PSBT: $error" }
            uiState =
              when (error) {
                is InsufficientFunds -> ReceivedInsufficientFundsErrorUiState
                else -> ReceivedBdkErrorUiState
              }
          }
        )
      ReceivedBdkErrorUiState ->
        ErrorFormBodyModel(
          title = "We couldn’t send this transaction",
          subline = "We are looking into this. Please try again later.",
          primaryButton = ButtonDataModel(text = "Done", onClick = props.onExit),
          eventTrackerScreenId = null
        ).asModalScreen()
      ReceivedInsufficientFundsErrorUiState ->
        ErrorFormBodyModel(
          title = "We couldn’t send this transaction",
          subline = "The amount you are trying to send is too high. Please decrease the amount and try again.",
          primaryButton = ButtonDataModel(text = "Go Back", onClick = props.onBack),
          eventTrackerScreenId = null
        ).asModalScreen()
      is ReceivedServerSigningErrorUiState ->
        ReceivedServerSigningErrorBodyModel(
          onExit = props.onExit,
          onContinue = {
            uiState =
              SigningWithHardwareUiState(
                appSignedPsbt = state.appSignedPsbt
              )
          }
        ).asModalScreen()
      is SigningWithHardwareUiState ->
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              commands.signTransaction(
                session = session,
                psbt = state.appSignedPsbt,
                spendingKeyset = props.account.keybox.activeSpendingKeyset
              )
            },
            onCancel = {
              uiState =
                ViewingTransferConfirmationUiState(
                  appSignedPsbt = state.appSignedPsbt
                )
            },
            onSuccess = { appAndHwSignedPsbt ->
              uiState =
                BroadcastingTransactionUiState(
                  twoOfThreeSignedPsbt = appAndHwSignedPsbt
                )
            },
            isHardwareFake = props.account.config.isHardwareFake,
            screenPresentationStyle = Modal,
            eventTrackerContext = NfcEventTrackerScreenIdContext.SIGN_TRANSACTION,
            shouldShowLongRunningOperation = true
          )
        )
      is ViewingTransferConfirmationUiState ->
        ViewConfirmation(
          props = props,
          state = state,
          selectedPriority = selectedPriority,
          onConfirm = {
            uiState =
              if (props.requiredSigner == F8e && props.spendingLimit != null) {
                SigningWithServerUiState(
                  appSignedPsbt = state.appSignedPsbt,
                  spendingLimit = props.spendingLimit
                )
              } else {
                SigningWithHardwareUiState(
                  appSignedPsbt = state.appSignedPsbt
                )
              }
          },
          onNetworkFees = {
            uiState = state.copy(sheetState = InfoSheet)
          },
          // only enable this handler when we have a psbt for each priority
          onArrivalTime =
            when (appSignedPsbts.size == EstimatedTransactionPriority.entries.size) {
              true -> {
                { uiState = state.copy(sheetState = FeeSelectionSheet) }
              }
              false -> null
            },
          onCloseSheet = {
            uiState = state.copy(sheetState = Hidden)
          },
          onFeeOptionSelected = { priority: EstimatedTransactionPriority ->
            val psbt =
              appSignedPsbts[priority]
                ?: error("Unable to select psbt from map, this shouldn’t happen")
            uiState = ViewingTransferConfirmationUiState(appSignedPsbt = psbt)
            selectedPriority = priority
          }
        )
    }
  }

  private data class ReceivedServerSigningErrorBodyModel(
    val onExit: () -> Unit,
    val onContinue: () -> Unit,
  ) : FormBodyModel(
      id = null,
      toolbar =
        ToolbarModel(
          leadingAccessory =
            ToolbarAccessoryModel.ButtonAccessory(
              model =
                ButtonModel(
                  text = "Cancel",
                  treatment = ButtonModel.Treatment.TertiaryDestructive,
                  size = ButtonModel.Size.Compact,
                  onClick = StandardClick(onExit)
                )
            )
        ),
      eventTrackerContext = null,
      onBack = onExit,
      header =
        FormHeaderModel(
          icon = Icon.LargeIconWarningFilled,
          headline = "We couldn’t send this as a mobile-only transaction",
          subline = "Please use your hardware device to confirm this transaction.",
          alignment = FormHeaderModel.Alignment.LEADING
        ),
      primaryButton =
        BitkeyInteractionButtonModel(
          text = "Continue",
          size = ButtonModel.Size.Footer,
          onClick = StandardClick(onContinue)
        ),
      renderContext = RenderContext.Screen,
      eventTrackerShouldTrack = false
    )

  @Composable
  private fun BroadcastingTransactionEffect(
    props: TransferConfirmationUiProps,
    state: BroadcastingTransactionUiState,
    selectedPriority: EstimatedTransactionPriority,
    onBdkError: () -> Unit,
  ) {
    LaunchedEffect("broadcasting-txn") {
      transactionsService
        .broadcast(
          psbt = state.twoOfThreeSignedPsbt,
          estimatedTransactionPriority = selectedPriority
        )
        .onSuccess {
          transactionPriorityPreference.set(selectedPriority)
          props.onTransferInitiated(state.twoOfThreeSignedPsbt, selectedPriority)
        }
        .logFailure { "Error broadcasting regular transaction." }
        .onFailure {
          when (props.requiredSigner) {
            Hardware -> onBdkError()
            F8e -> {
              // On failure, the Server already published the transaction, so no user error is
              // presented. This can happen due to user-configured server settings or network
              // that are unrelated to the broadcast done by the Server.
              transactionsService.syncTransactions()
              transactionPriorityPreference.set(props.selectedPriority)
              props.onTransferInitiated(state.twoOfThreeSignedPsbt, selectedPriority)
            }
          }
        }
    }
  }

  @Composable
  private fun CreatingAppSignedPsbt(
    props: TransferConfirmationUiProps,
    selectedPriority: EstimatedTransactionPriority,
    onAppSignSuccess: (
      Map<EstimatedTransactionPriority, Psbt>,
    ) -> Unit,
    onAppSignError: () -> Unit,
    onPsbtCreateError: (BdkError) -> Unit,
  ): ScreenModel {
    LaunchedEffect("create-app-signed-psbt") {
      val psbts =
        props.fees.entries.associate { entry ->
          val constructionMethod = SpendingWallet.PsbtConstructionMethod.Regular(
            recipientAddress = props.recipientAddress,
            amount = props.sendAmount,
            feePolicy = FeePolicy.Absolute(entry.value)
          )
          val psbtResult =
            createAppSignedPsbt(
              constructionMethod = constructionMethod
            )

          // If we can't build or sign the psbt for the selected fee, we will invoke the error handlers
          if (entry.key == selectedPriority && psbtResult.isErr) {
            when (val error = psbtResult.error) {
              is BdkError -> onPsbtCreateError(error)
              else -> onAppSignError()
            }
          }

          entry.key to psbtResult.get()
        }.filterNotNull()
          .toImmutableMap()

      // only continue if we are able to select the psbt of the original selected fee
      psbts[selectedPriority]?.let { onAppSignSuccess(psbts) }
    }

    return LoadingBodyModel(
      message = "Loading transaction...",
      onBack = props.onExit,
      id = SendEventTrackerScreenId.SEND_CREATING_PSBT_LOADING,
      eventTrackerShouldTrack = false
    ).asModalFullScreen()
  }

  @Composable
  private fun SigningWithServerEffect(
    state: SigningWithServerUiState,
    onSignSuccess: (Psbt) -> Unit,
    onSignError: () -> Unit,
  ) {
    LaunchedEffect("signing-with-server") {
      mobilePayService
        .signPsbtWithMobilePay(
          psbt = state.appSignedPsbt
        )
        .onSuccess { appAndServerSignedPsbt ->
          log { "Successfully signed psbt with server: $appAndServerSignedPsbt" }
          onSignSuccess(appAndServerSignedPsbt)
        }
        .onFailure {
          onSignError()
        }
    }
  }

  @Composable
  private fun ViewConfirmation(
    props: TransferConfirmationUiProps,
    state: ViewingTransferConfirmationUiState,
    selectedPriority: EstimatedTransactionPriority,
    onConfirm: () -> Unit,
    onNetworkFees: () -> Unit,
    onArrivalTime: (() -> Unit)?,
    onCloseSheet: () -> Unit,
    onFeeOptionSelected: (EstimatedTransactionPriority) -> Unit,
  ): ScreenModel {
    val transferBitcoinAmount = BitcoinMoney.sats(state.appSignedPsbt.amountSats.toBigInteger())
    val feeBitcoinAmount = state.appSignedPsbt.fee

    val transactionDetails = when (props.variant) {
      TransferConfirmationScreenVariant.Regular,
      TransferConfirmationScreenVariant.SpeedUp,
      -> TransactionDetails.Regular(
        transferAmount = transferBitcoinAmount,
        feeAmount = feeBitcoinAmount,
        estimatedTransactionPriority = selectedPriority
      )
      is TransferConfirmationScreenVariant.Sell -> TransactionDetails.Sell(
        transferAmount = transferBitcoinAmount,
        feeAmount = feeBitcoinAmount,
        estimatedTransactionPriority = selectedPriority
      )
    }

    val transactionDetailsCard = transactionDetailsCardUiStateMachine.model(
      props = TransactionDetailsCardUiProps(
        transactionDetails = transactionDetails,
        exchangeRates = props.exchangeRates,
        variant = props.variant
      )
    )

    val variant = props.variant

    return TransferConfirmationScreenModel(
      onBack = props.onExit,
      onCancel = props.onExit,
      variant = variant,
      recipientAddress = props.recipientAddress.chunkedAddress(),
      transactionDetails = transactionDetailsCard,
      requiresHardware = props.requiredSigner == Hardware,
      confirmButtonEnabled = true,
      onConfirmClick = onConfirm,
      onNetworkFeesClick = onNetworkFees,
      onArrivalTimeClick = if (variant is TransferConfirmationScreenVariant.Sell) {
        null
      } else {
        onArrivalTime
      },
      errorOverlayModel = when (state.sheetState) {
        InfoSheet ->
          SheetModel(
            onClosed = onCloseSheet,
            body =
              NetworkFeesInfoSheetModel(
                onBack = onCloseSheet
              )
          )
        FeeSelectionSheet ->
          SheetModel(
            onClosed = onCloseSheet,
            body = FeeSelectionSheetModel(
              onBack = onCloseSheet,
              feeOptionList = feeOptionListUiStateMachine.model(
                props = FeeOptionListProps(
                  transactionBaseAmount = BitcoinMoney.sats(
                    state.appSignedPsbt.amountSats.toBigInteger()
                  ),
                  exchangeRates = props.exchangeRates,
                  fees = props.fees,
                  defaultPriority = selectedPriority,
                  onOptionSelected = onFeeOptionSelected
                )
              )
            )
          )

        Hidden -> null
      }
    )
  }

  private suspend fun createAppSignedPsbt(
    constructionMethod: SpendingWallet.PsbtConstructionMethod,
  ): Result<Psbt, Throwable> =
    coroutineBinding {
      val wallet = transactionsService.spendingWallet().value
      ensureNotNull(wallet) { Error("No spending wallet found.") }

      wallet
        .createSignedPsbt(constructionType = constructionMethod)
        .bind()
    }
}

private sealed interface TransferConfirmationUiState {
  /**
   * Creating an unsigned psbt to start the confirmation flow
   */
  data object CreatingAppSignedPsbtUiState : TransferConfirmationUiState

  /**
   * Viewing transfer confirmation with info of transfer
   *
   * @property appSignedPsbt - the app-signed psbt associated with the transfer
   */
  data class ViewingTransferConfirmationUiState(
    val appSignedPsbt: Psbt,
    val sheetState: SheetState = Hidden,
  ) : TransferConfirmationUiState {
    sealed interface SheetState {
      data object Hidden : SheetState

      data object InfoSheet : SheetState

      data object FeeSelectionSheet : SheetState
    }
  }

  /**
   * Signing the psbt via hardware
   *
   * @property appSignedPsbt - the app-signed psbt associated with the transfer
   */
  data class SigningWithHardwareUiState(
    val appSignedPsbt: Psbt,
  ) : TransferConfirmationUiState

  /**
   * Signing the psbt via server
   *
   * @property appSignedPsbt - the psbt to be signed
   * @property spendingLimit - the [SpendingLimit] associated with the server signing
   */
  data class SigningWithServerUiState(
    val appSignedPsbt: Psbt,
    val spendingLimit: SpendingLimit,
  ) : TransferConfirmationUiState

  /**
   * Broadcasting the transaction
   *
   * @property twoOfThreeSignedPsbt - a psbt signed via app & (hardware | server)
   */
  data class BroadcastingTransactionUiState(
    val twoOfThreeSignedPsbt: Psbt,
  ) : TransferConfirmationUiState

  sealed interface ErrorUiState : TransferConfirmationUiState {
    /**
     * Not enough funds to generate a transfer
     */
    data object ReceivedInsufficientFundsErrorUiState : ErrorUiState

    /**
     * Received a BDK error while creating, signing or broadcasting the psbt
     */
    data object ReceivedBdkErrorUiState : ErrorUiState

    /**
     * Received a server signing error
     *
     * @property appSignedPsbt - the app-signed psbt sent to be signed
     */
    data class ReceivedServerSigningErrorUiState(
      val appSignedPsbt: Psbt,
    ) : ErrorUiState
  }
}

private fun Map<EstimatedTransactionPriority, Psbt?>.filterNotNull():
  Map<EstimatedTransactionPriority, Psbt> =
  this.mapNotNull { entry ->
    entry.value?.let { value ->
      entry.key to value
    }
  }.toMap()
