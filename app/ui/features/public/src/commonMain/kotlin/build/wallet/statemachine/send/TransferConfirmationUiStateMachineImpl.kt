package build.wallet.statemachine.send

import androidx.compose.runtime.*
import bitkey.account.isW3Hardware
import bitkey.ui.verification.TxVerificationAppSegment
import bitkey.verification.ConfirmationState
import bitkey.verification.TxVerificationApproval
import bitkey.verification.TxVerificationService
import bitkey.verification.VerificationRequiredError
import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.analytics.events.screen.id.TxVerificationEventTrackerScreenId
import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.FunctionalityFeatureStates
import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkError.InsufficientFunds
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.TransactionPriorityPreference
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.factor.SigningFactor
import build.wallet.bitkey.factor.SigningFactor.F8e
import build.wallet.bitkey.factor.SigningFactor.Hardware
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.coroutines.scopes.mapAsStateFlow
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.ensureNotNull
import build.wallet.feature.flags.TxVerificationFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.limit.DailySpendingLimitStatus
import build.wallet.limit.MobilePayData
import build.wallet.limit.MobilePayService
import build.wallet.logging.logDebug
import build.wallet.logging.logError
import build.wallet.logging.logFailure
import build.wallet.logging.logWarn
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.nfc.NfcConfirmableSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcConfirmableSessionUiStateMachine
import build.wallet.statemachine.send.TransferConfirmationUiState.*
import build.wallet.statemachine.send.TransferConfirmationUiState.ErrorUiState.*
import build.wallet.statemachine.send.TransferConfirmationUiState.ViewingTransferConfirmationUiState.SheetState.*
import build.wallet.statemachine.send.fee.FeeOptionListProps
import build.wallet.statemachine.send.fee.FeeOptionListUiStateMachine
import build.wallet.statemachine.transactions.TransactionDetails
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Companion.BitkeyInteractionButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap

@BitkeyInject(ActivityScope::class)
class TransferConfirmationUiStateMachineImpl(
  private val transactionDetailsCardUiStateMachine: TransactionDetailsCardUiStateMachine,
  private val nfcSessionUIStateMachine: NfcConfirmableSessionUiStateMachine,
  private val transactionPriorityPreference: TransactionPriorityPreference,
  private val feeOptionListUiStateMachine: FeeOptionListUiStateMachine,
  private val bitcoinWalletService: BitcoinWalletService,
  private val mobilePayService: MobilePayService,
  private val appFunctionalityService: AppFunctionalityService,
  private val accountService: AccountService,
  private val accountConfigService: bitkey.account.AccountConfigService,
  private val txVerificationService: TxVerificationService,
  private val txVerificationFeatureFlag: TxVerificationFeatureFlag,
) : TransferConfirmationUiStateMachine {
  @Composable
  override fun model(props: TransferConfirmationUiProps): ScreenModel {
    val scope = rememberStableCoroutineScope()

    var uiState: TransferConfirmationUiState by remember {
      mutableStateOf(CreatingAppSignedPsbtUiState)
    }

    var selectedPriority: EstimatedTransactionPriority by remember {
      mutableStateOf(props.selectedPriority)
    }

    var appSignedPsbts: Map<EstimatedTransactionPriority, Psbt> by remember {
      mutableStateOf(persistentMapOf())
    }

    val isW3 = accountConfigService.activeOrDefaultConfig().value.isW3Hardware

    val mobilePayAvailability by remember {
      appFunctionalityService.status
        .mapAsStateFlow(scope) { it.featureStates.mobilePay }
    }.collectAsState()

    val spendingLimit by remember {
      mobilePayService.mobilePayData
        .mapAsStateFlow(scope) {
          when (it) {
            is MobilePayData.MobilePayEnabledData -> it.activeSpendingLimit
            else -> null
          }
        }
    }.collectAsState()

    val requiredSigner by remember(mobilePayAvailability) {
      derivedStateOf {
        val status = mobilePayService.getDailySpendingLimitStatus(
          transactionAmount = props.sendAmount
        )

        when (mobilePayAvailability) {
          FunctionalityFeatureStates.FeatureState.Available -> when (status) {
            DailySpendingLimitStatus.RequiresHardware -> Hardware
            DailySpendingLimitStatus.MobilePayAvailable -> F8e
          }
          else -> Hardware
        }
      }
    }

    when (val state = uiState) {
      is CheckVerificationUiState -> CheckVerificationStateEffect(
        props = props,
        state = state,
        setUiState = { uiState = it }
      )
      is VerifyingTransactionUiState -> VerifyTransactionEffect(
        state = state,
        setUiState = { uiState = it }
      )
      is RequestHardwareGrantUiState -> RequestHardwareGrantEffect(
        state = state,
        setUiState = { uiState = it }
      )
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
                twoOfThreeSignedPsbt = appAndServerSignedPsbt,
                cosigner = F8e
              )
          },
          onSignError = {
            uiState =
              ReceivedServerSigningErrorUiState(
                state.appSignedPsbt,
                state.grant
              )
          }
        )
      else -> Unit
    }

    return when (val state = uiState) {
      is BroadcastingTransactionUiState, is SigningWithServerUiState ->
        LoadingBodyModel(
          title = "Initiating transfer...",
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
            val psbt = psbts[selectedPriority]
              ?: error("This callback should not be invoked without selected priority, this shouldn’t happen")
            uiState = CheckVerificationUiState(psbt)
          },
          onAppSignError = { cause ->
            logError(throwable = cause) { "Unable to sign PSBT" }
            uiState = ReceivedBdkErrorUiState
          },
          onPsbtCreateError = { error ->
            logError(throwable = error) { "Unable to create PSBT: $error" }
            uiState =
              when (error) {
                is InsufficientFunds -> ReceivedInsufficientFundsErrorUiState
                else -> ReceivedBdkErrorUiState
              }
          }
        )
      is CheckVerificationUiState, is RequestHardwareGrantUiState -> LoadingBodyModel(
        onBack = props.onBack,
        id = null,
        eventTrackerShouldTrack = false
      ).asModalScreen()
      is ConfirmVerificationUiState -> VerifyConfirmation(
        onBack = props.onBack,
        onContinue = {
          uiState = VerifyingTransactionUiState(
            psbt = state.psbt
          )
        }
      ).asModalScreen()
      is VerifyingTransactionUiState -> LoadingBodyModel(
        title = "Waiting for verification...",
        onBack = props.onBack,
        id = null,
        eventTrackerShouldTrack = false,
        primaryButton = ButtonModel(
          text = "Resend Email",
          onClick = StandardClick {
            uiState = ConfirmVerificationUiState(state.psbt)
          },
          size = ButtonModel.Size.Footer
        ),
        secondaryButton = ButtonModel(
          text = "Cancel verification",
          onClick = StandardClick {
            uiState = CanceledVerificationUiState
          },
          treatment = ButtonModel.Treatment.SecondaryDestructive,
          size = ButtonModel.Size.Footer
        )
      ).asModalScreen()
      is RejectedConfirmationUiState -> TransactionCanceledBodyModel(
        id = TxVerificationEventTrackerScreenId.VERIFICATION_REJECTED,
        onExit = props.onBack
      ).asModalScreen()
      is ExpiredConfirmationUiState -> TransactionCanceledBodyModel(
        id = TxVerificationEventTrackerScreenId.VERIFICATION_EXPIRED,
        onExit = props.onBack
      ).asModalScreen()
      is VerificationErrorUiState -> ErrorFormBodyModel(
        errorData = state.error,
        title = "We couldn't verify this transaction",
        subline = "Please try again later.",
        primaryButton = ButtonDataModel(text = "Got it", onClick = props.onExit),
        eventTrackerScreenId = TxVerificationEventTrackerScreenId.VERIFICATION_ERROR
      ).asModalScreen()
      is CanceledVerificationUiState -> TransactionCanceledBodyModel(
        id = TxVerificationEventTrackerScreenId.VERIFICATION_CANCELED,
        onExit = props.onBack
      ).asModalScreen()
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
                appSignedPsbt = state.appSignedPsbt,
                grant = state.grant
              )
          }
        ).asModalScreen()
      is SigningWithHardwareUiState ->
        nfcSessionUIStateMachine.model(
          NfcConfirmableSessionUIStateMachineProps(
            session = { session, commands ->
              // TODO: refactor NFC APIs to use Result
              val account = accountService.getAccount<FullAccount>().getOrThrow()
              commands.signTransaction(
                session = session,
                psbt = state.appSignedPsbt,
                spendingKeyset = account.keybox.activeSpendingKeyset
              )
            },
            onCancel = {
              uiState = ViewingTransferConfirmationUiState(
                appSignedPsbt = state.appSignedPsbt
              )
            },
            onSuccess = { psbt ->
              uiState =
                BroadcastingTransactionUiState(
                  twoOfThreeSignedPsbt = psbt,
                  cosigner = Hardware
                )
            },
            screenPresentationStyle = Modal,
            eventTrackerContext = NfcEventTrackerScreenIdContext.SIGN_TRANSACTION,
            shouldShowLongRunningOperation = true
          )
        )
      is ViewingTransferConfirmationUiState ->
        ViewConfirmation(
          props = props,
          state = state,
          isW3 = isW3,
          selectedPriority = selectedPriority,
          requiredSigner = requiredSigner,
          onConfirm = {
            uiState =
              if (requiredSigner == F8e && spendingLimit != null) {
                SigningWithServerUiState(
                  appSignedPsbt = state.appSignedPsbt,
                  grant = state.grant
                )
              } else {
                SigningWithHardwareUiState(
                  appSignedPsbt = state.appSignedPsbt,
                  grant = state.grant
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
      id = SendEventTrackerScreenId.SEND_SERVER_SIGNING_ERROR,
      toolbar = ToolbarModel(leadingAccessory = BackAccessory(onExit)),
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
      bitcoinWalletService
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
          when (state.cosigner) {
            Hardware -> onBdkError()
            F8e -> {
              // On failure, the Server already published the transaction, so no user error is
              // presented. This can happen due to user-configured server settings or network
              // that are unrelated to the broadcast done by the Server.
              bitcoinWalletService.sync()
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
    onAppSignError: (Throwable) -> Unit,
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
              else -> onAppSignError(error)
            }
          }

          entry.key to psbtResult.get()
        }.filterNotNull()
          .toImmutableMap()

      // only continue if we are able to select the psbt of the original selected fee
      psbts[selectedPriority]?.let { onAppSignSuccess(psbts) }
    }

    return LoadingBodyModel(
      title = "Loading transaction...",
      onBack = props.onBack,
      id = SendEventTrackerScreenId.SEND_CREATING_PSBT_LOADING,
      eventTrackerShouldTrack = false
    ).asModalFullScreen()
  }

  @Composable
  private fun CheckVerificationStateEffect(
    props: TransferConfirmationUiProps,
    state: CheckVerificationUiState,
    setUiState: (TransferConfirmationUiState) -> Unit,
  ) = LaunchedEffect("Check verification", state.psbt.id) {
    setUiState(
      when {
        !txVerificationFeatureFlag.isEnabled() -> ViewingTransferConfirmationUiState(appSignedPsbt = state.psbt)
        txVerificationService.isVerificationRequired(
          state.psbt.amountBtc,
          props.exchangeRates
        ) -> ConfirmVerificationUiState(psbt = state.psbt)
        else -> RequestHardwareGrantUiState(psbt = state.psbt)
      }
    )
  }

  @Composable
  private fun RequestHardwareGrantEffect(
    state: RequestHardwareGrantUiState,
    setUiState: (TransferConfirmationUiState) -> Unit,
  ) = LaunchedEffect("Request hardware grant", state.psbt.id) {
    txVerificationService.requestGrant(state.psbt)
      .onFailure { error ->
        if (error is VerificationRequiredError) {
          logWarn {
            "Verification required for transaction by server. Redirecting user to verification confirmation"
          }
          setUiState(ConfirmVerificationUiState(psbt = state.psbt))
        } else {
          logError(throwable = error) { "Failed to request hardware grant" }
          setUiState(
            VerificationErrorUiState(
              error = ErrorData(
                segment = TxVerificationAppSegment.Transaction,
                actionDescription = "Requesting a hardware grant from server",
                cause = error
              )
            )
          )
        }
      }
      .onSuccess { grant ->
        setUiState(
          ViewingTransferConfirmationUiState(
            appSignedPsbt = state.psbt,
            grant = grant
          )
        )
      }
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
          psbt = state.appSignedPsbt,
          grant = state.grant
        )
        .onSuccess { appAndServerSignedPsbt ->
          logDebug { "Successfully signed psbt with server: $appAndServerSignedPsbt" }
          onSignSuccess(appAndServerSignedPsbt)
        }
        .onFailure {
          onSignError()
        }
    }
  }

  @Composable
  private fun VerifyTransactionEffect(
    state: VerifyingTransactionUiState,
    setUiState: (TransferConfirmationUiState) -> Unit,
  ) = LaunchedEffect("verify", state.psbt.id) {
    txVerificationService.requestVerification(state.psbt)
      .onFailure { error ->
        setUiState(
          VerificationErrorUiState(
            error = ErrorData(
              segment = TxVerificationAppSegment.Transaction,
              actionDescription = "Requesting verification from server",
              cause = error
            )
          )
        )
      }
      .get()
      ?.collect { confirmationState ->
        when (confirmationState) {
          is ConfirmationState.Confirmed -> {
            setUiState(
              ViewingTransferConfirmationUiState(
                appSignedPsbt = state.psbt,
                grant = confirmationState.data
              )
            )
          }
          ConfirmationState.Rejected -> {
            setUiState(RejectedConfirmationUiState)
          }
          ConfirmationState.Expired -> {
            setUiState(ExpiredConfirmationUiState)
          }
          ConfirmationState.Pending -> {
            // No-op - Waiting for verification to complete
          }
        }
      }
  }

  @Composable
  private fun ViewConfirmation(
    props: TransferConfirmationUiProps,
    state: ViewingTransferConfirmationUiState,
    selectedPriority: EstimatedTransactionPriority,
    requiredSigner: SigningFactor,
    isW3: Boolean,
    onConfirm: () -> Unit,
    onNetworkFees: () -> Unit,
    onArrivalTime: (() -> Unit)?,
    onCloseSheet: () -> Unit,
    onFeeOptionSelected: (EstimatedTransactionPriority) -> Unit,
  ): ScreenModel {
    val transferBitcoinAmount = BitcoinMoney.sats(state.appSignedPsbt.amountSats.toBigInteger())
    val feeBitcoinAmount = state.appSignedPsbt.fee

    val transactionDetails = TransactionDetails.Regular(
      transferAmount = transferBitcoinAmount,
      feeAmount = feeBitcoinAmount.amount,
      estimatedTransactionPriority = selectedPriority
    )

    val transactionDetailsCard = transactionDetailsCardUiStateMachine.model(
      props = TransactionDetailsCardUiProps(
        transactionDetails = transactionDetails,
        exchangeRates = props.exchangeRates,
        variant = props.variant
      )
    )

    val variant = props.variant

    return TransferConfirmationScreenModel(
      onBack = props.onBack,
      variant = variant,
      recipientAddress = props.recipientAddress,
      transactionDetails = transactionDetailsCard,
      requiresHardware = requiredSigner == Hardware,
      confirmButtonEnabled = true,
      onConfirmClick = onConfirm,
      onNetworkFeesClick = onNetworkFees,
      onArrivalTimeClick = if (variant is TransferConfirmationScreenVariant.Sell) {
        null
      } else {
        onArrivalTime
      },
      requiresHardwareReview = isW3
    ).asModalFullScreen(
      bottomSheetModel = when (state.sheetState) {
        InfoSheet -> SheetModel(
          onClosed = onCloseSheet,
          body = NetworkFeesInfoSheetModel(onBack = onCloseSheet)
        )
        FeeSelectionSheet -> SheetModel(
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
      val wallet = bitcoinWalletService.spendingWallet().value
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
   * Loading screen while checking if the transaction requires verification.
   */
  data class CheckVerificationUiState(
    val psbt: Psbt,
  ) : TransferConfirmationUiState

  /**
   * Notification to the user that the transaction requires verification.
   */
  data class ConfirmVerificationUiState(
    val psbt: Psbt,
  ) : TransferConfirmationUiState

  /**
   * Loading screen while requesting and waiting for the user to verify the transaction.
   */
  data class VerifyingTransactionUiState(
    val psbt: Psbt,
  ) : TransferConfirmationUiState

  /**
   * Loading screen while requesting a hardware grant from the server to sign with hardware.
   */
  data class RequestHardwareGrantUiState(
    val psbt: Psbt,
  ) : TransferConfirmationUiState

  /**
   * Transaction canceled notification when the user has indicated a problem during verification.
   */
  data object RejectedConfirmationUiState : TransferConfirmationUiState

  /**
   * Transaction canceled notification when the user has not completed verification in time.
   */
  data object ExpiredConfirmationUiState : TransferConfirmationUiState

  /**
   * Transaction canceled notification when the user has explicitly canceled the verification.
   */
  data object CanceledVerificationUiState : TransferConfirmationUiState

  /**
   * Error state shown if verification fails due to an unexpected error.
   */
  data class VerificationErrorUiState(val error: ErrorData) : TransferConfirmationUiState

  /**
   * Viewing transfer confirmation with info of transfer
   *
   * @property appSignedPsbt - the app-signed psbt associated with the transfer
   */
  data class ViewingTransferConfirmationUiState(
    val appSignedPsbt: Psbt,
    val grant: TxVerificationApproval? = null,
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
    val grant: TxVerificationApproval?,
  ) : TransferConfirmationUiState

  /**
   * Signing the psbt via server
   *
   * @property appSignedPsbt - the psbt to be signed
   */
  data class SigningWithServerUiState(
    val appSignedPsbt: Psbt,
    val grant: TxVerificationApproval?,
  ) : TransferConfirmationUiState

  /**
   * Broadcasting the transaction
   *
   * @property twoOfThreeSignedPsbt - a psbt signed via app & (hardware | server)
   * @property cosigner - the factor (hardware or server) that co-signed with the app
   */
  data class BroadcastingTransactionUiState(
    val twoOfThreeSignedPsbt: Psbt,
    val cosigner: SigningFactor,
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
      val grant: TxVerificationApproval?,
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
