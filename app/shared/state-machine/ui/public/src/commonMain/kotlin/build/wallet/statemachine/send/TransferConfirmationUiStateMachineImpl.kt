package build.wallet.statemachine.send

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkError.InsufficientFunds
import build.wallet.bitcoin.blockchain.BitcoinBlockchain
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.OutgoingTransactionDetail
import build.wallet.bitcoin.transactions.OutgoingTransactionDetailRepository
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.TransactionPriorityPreference
import build.wallet.bitcoin.transactions.toDuration
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.factor.SigningFactor.F8e
import build.wallet.bitkey.factor.SigningFactor.Hardware
import build.wallet.f8e.mobilepay.MobilePaySigningService
import build.wallet.keybox.wallet.AppSpendingWalletProvider
import build.wallet.limit.SpendingLimit
import build.wallet.logging.LogLevel.Error
import build.wallet.logging.log
import build.wallet.logging.logFailure
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.send.TransferConfirmationUiProps.Variant
import build.wallet.statemachine.send.TransferConfirmationUiState.BroadcastingTransactionUiState
import build.wallet.statemachine.send.TransferConfirmationUiState.CreatingAppSignedPsbtUiState
import build.wallet.statemachine.send.TransferConfirmationUiState.ErrorUiState.ReceivedBdkErrorUiState
import build.wallet.statemachine.send.TransferConfirmationUiState.ErrorUiState.ReceivedInsufficientFundsErrorUiState
import build.wallet.statemachine.send.TransferConfirmationUiState.ErrorUiState.ReceivedServerSigningErrorUiState
import build.wallet.statemachine.send.TransferConfirmationUiState.SigningWithHardwareUiState
import build.wallet.statemachine.send.TransferConfirmationUiState.SigningWithServerUiState
import build.wallet.statemachine.send.TransferConfirmationUiState.ViewingTransferConfirmationUiState
import build.wallet.statemachine.send.TransferConfirmationUiState.ViewingTransferConfirmationUiState.SheetState.FeeSelectionSheet
import build.wallet.statemachine.send.TransferConfirmationUiState.ViewingTransferConfirmationUiState.SheetState.Hidden
import build.wallet.statemachine.send.TransferConfirmationUiState.ViewingTransferConfirmationUiState.SheetState.InfoSheet
import build.wallet.statemachine.send.fee.FeeOptionListProps
import build.wallet.statemachine.send.fee.FeeOptionListUiStateMachine
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Companion.BitkeyInteractionButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap

class TransferConfirmationUiStateMachineImpl(
  private val mobilePaySigningService: MobilePaySigningService,
  private val bitcoinBlockchain: BitcoinBlockchain,
  private val transactionDetailsCardUiStateMachine: TransactionDetailsCardUiStateMachine,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val transactionPriorityPreference: TransactionPriorityPreference,
  private val appSpendingWalletProvider: AppSpendingWalletProvider,
  private val feeOptionListUiStateMachine: FeeOptionListUiStateMachine,
  private val outgoingTransactionDetailRepository: OutgoingTransactionDetailRepository,
) : TransferConfirmationUiStateMachine {
  @Composable
  override fun model(props: TransferConfirmationUiProps): ScreenModel {
    var uiState: TransferConfirmationUiState by remember {
      mutableStateOf(CreatingAppSignedPsbtUiState)
    }

    var selectedPriority: EstimatedTransactionPriority by remember {
      mutableStateOf(
        when (props.transferVariant) {
          is Variant.Regular -> {
            props.transferVariant.selectedPriority
          }
          is Variant.SpeedUp -> {
            // By default, we prescribe the fastest priority in this flow.
            EstimatedTransactionPriority.FASTEST
          }
        }
      )
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
          props = props,
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
        FormBodyModel(
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
                      onClick = StandardClick(props.onExit)
                    )
                )
            ),
          eventTrackerScreenIdContext = null,
          onBack = props.onExit,
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
              onClick =
                {
                  uiState =
                    SigningWithHardwareUiState(
                      appSignedPsbt = state.appSignedPsbt
                    )
                }
            ),
          renderContext = RenderContext.Screen,
          eventTrackerShouldTrack = false
        ).asModalScreen()
      is SigningWithHardwareUiState ->
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              commands.signTransaction(
                session = session,
                psbt = state.appSignedPsbt,
                spendingKeyset = props.accountData.account.keybox.activeSpendingKeyset
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
            isHardwareFake = props.accountData.account.config.isHardwareFake,
            screenPresentationStyle = Modal,
            eventTrackerContext = NfcEventTrackerScreenIdContext.SIGN_TRANSACTION
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

  @Composable
  private fun BroadcastingTransactionEffect(
    props: TransferConfirmationUiProps,
    state: BroadcastingTransactionUiState,
    selectedPriority: EstimatedTransactionPriority,
    onBdkError: () -> Unit,
  ) {
    LaunchedEffect("broadcasting-txn") {
      bitcoinBlockchain.broadcast(psbt = state.twoOfThreeSignedPsbt)
        .onSuccess {
          props.accountData.transactionsData.syncTransactions()
          transactionPriorityPreference.set(selectedPriority)
          props.onTransferInitiated(state.twoOfThreeSignedPsbt, selectedPriority)
          // When we successfully broadcast the transaction, store the transaction details and
          // exchange rate.
          outgoingTransactionDetailRepository.persistDetails(
            details =
              OutgoingTransactionDetail(
                broadcastDetail = it,
                exchangeRates = props.exchangeRates,
                estimatedConfirmationTime = it.broadcastTime.plus(selectedPriority.toDuration())
              )
          )
        }
        .logFailure { "Failed to broadcast transaction" }
        .onFailure {
          when (props.requiredSigner) {
            Hardware -> onBdkError()
            F8e -> {
              // On failure, the Server already published the transaction, so no user error is
              // presented. This can happen due to user-configured server settings or network
              // that are unrelated to the broadcast done by the Server.
              props.accountData.transactionsData.syncTransactions()
              when (val variant = props.transferVariant) {
                is TransferConfirmationUiProps.Variant.Regular -> {
                  transactionPriorityPreference.set(variant.selectedPriority)
                }
                else -> Unit
              }
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
          val constructionMethod =
            when (props.transferVariant) {
              is Variant.Regular ->
                SpendingWallet.PsbtConstructionMethod.Regular(
                  recipientAddress = props.recipientAddress,
                  amount = props.sendAmount,
                  feePolicy = FeePolicy.Absolute(entry.value)
                )
              is Variant.SpeedUp ->
                SpendingWallet.PsbtConstructionMethod.BumpFee(
                  txid = props.transferVariant.txid,
                  feeRate = props.transferVariant.newFeeRate
                )
            }
          val psbtResult =
            createAppSignedPsbt(
              account = props.accountData.account,
              constructionMethod = constructionMethod
            )

          // If we can't build or sign the psbt for the selected fee, we will invoke the error handlers
          if (entry.key == selectedPriority && psbtResult is Err) {
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
    props: TransferConfirmationUiProps,
    state: SigningWithServerUiState,
    onSignSuccess: (Psbt) -> Unit,
    onSignError: () -> Unit,
  ) {
    LaunchedEffect("signing-with-server") {
      mobilePaySigningService
        .signWithSpecificKeyset(
          f8eEnvironment = props.accountData.account.config.f8eEnvironment,
          fullAccountId = props.accountData.account.accountId,
          keysetId = props.accountData.account.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId,
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
    val transactionDetail =
      when (props.transferVariant) {
        is Variant.Regular ->
          TransactionDetailType.Regular(
            transferBitcoinAmount = transferBitcoinAmount,
            feeBitcoinAmount = feeBitcoinAmount,
            estimatedTransactionPriority = selectedPriority
          )
        is Variant.SpeedUp ->
          TransactionDetailType.SpeedUp(
            transferBitcoinAmount = transferBitcoinAmount,
            feeBitcoinAmount = feeBitcoinAmount,
            oldFeeBitcoinAmount = props.transferVariant.oldFee.amount
          )
      }

    val transactionDetails =
      transactionDetailsCardUiStateMachine.model(
        props =
          TransactionDetailsCardUiProps(
            transactionDetail = transactionDetail,
            fiatCurrency = props.fiatCurrency,
            exchangeRates = props.exchangeRates
          )
      )

    return TransferConfirmationScreenModel(
      onBack = props.onExit,
      onCancel = props.onExit,
      variant = props.transferVariant,
      recipientAddress = props.recipientAddress.chunkedAddress(),
      transactionDetails = transactionDetails,
      requiresHardware = props.requiredSigner == Hardware,
      confirmButtonEnabled = true,
      onConfirmClick = onConfirm,
      onNetworkFeesClick = onNetworkFees,
      // Only make arrival time tappable if customer is not fee bumping.
      onArrivalTimeClick =
        when (props.transferVariant) {
          is Variant.Regular -> onArrivalTime
          else -> null
        },
      errorOverlayModel =
        when (state.sheetState) {
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
              body =
                FeeSelectionSheetModel(
                  onBack = onCloseSheet,
                  feeOptionList =
                    feeOptionListUiStateMachine.model(
                      props =
                        FeeOptionListProps(
                          accountData = props.accountData,
                          transactionBaseAmount =
                            BitcoinMoney.sats(
                              state.appSignedPsbt.amountSats.toBigInteger()
                            ),
                          fiatCurrency = props.fiatCurrency,
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
    account: FullAccount,
    constructionMethod: SpendingWallet.PsbtConstructionMethod,
  ): Result<Psbt, Throwable> =
    binding {
      val wallet =
        appSpendingWalletProvider
          .getSpendingWallet(account)
          .bind()

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
