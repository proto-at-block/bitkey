package build.wallet.statemachine.recovery.sweep

import androidx.compose.runtime.*
import bitkey.account.HardwareType
import bitkey.account.isW3Hardware
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.InactiveWalletSweepEventTrackerScreenId
import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logDebug
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.recovery.sweep.SweepContext
import build.wallet.recovery.sweep.SweepPsbt
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.data.recovery.sweep.SweepData
import build.wallet.statemachine.data.recovery.sweep.SweepData.*
import build.wallet.statemachine.data.recovery.sweep.SweepDataProps
import build.wallet.statemachine.data.recovery.sweep.SweepDataStateMachine
import build.wallet.statemachine.money.amount.MoneyAmountUiProps
import build.wallet.statemachine.money.amount.MoneyAmountUiStateMachine
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.send.*
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcSessionUiProps
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcSessionUiStateMachine
import build.wallet.statemachine.walletmigration.PrivateWalletMigrationAppSegment

@BitkeyInject(ActivityScope::class)
class SweepUiStateMachineImpl(
  private val signTransactionNfcSessionUiStateMachine: SignTransactionNfcSessionUiStateMachine,
  private val moneyAmountUiStateMachine: MoneyAmountUiStateMachine,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val sweepDataStateMachine: SweepDataStateMachine,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
) : SweepUiStateMachine {
  @Composable
  override fun model(props: SweepUiProps): ScreenModel {
    var screenState: ScreenState by remember { mutableStateOf(ScreenState.ShowingSweepState) }
    val sweepData = sweepDataStateMachine.model(
      SweepDataProps(
        hasAttemptedSweep = props.hasAttemptedSweep,
        keybox = props.account.keybox,
        sweepContext = props.sweepContext,
        onSuccess = props.onSuccess,
        onAttemptSweep = props.onAttemptSweep
      )
    )

    // Track which AwaitingHardwareSignedSweepsData instance we've initiated signing for
    // This prevents re-triggering when returning to ShowingSweepState with stale data
    var initiatedSigningForData: AwaitingHardwareSignedSweepsData? by remember { mutableStateOf(null) }

    return when (val uiState = screenState) {
      ScreenState.ShowingSweepState -> getSweepScreen(
        props = props,
        sweepData = sweepData,
        setState = { screenState = it },
        initiatedSigningForData = initiatedSigningForData,
        onInitiateSigning = { data -> initiatedSigningForData = data }
      )
      ScreenState.ShowingHelpText -> sweepInactiveHelpModel(
        id = InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_HELP,
        presentationStyle = props.presentationStyle,
        onLearnMore = {
          screenState = ScreenState.ShowingLearnMore("https://support.bitkey.world/hc/en-us/articles/28019865146516-How-do-I-access-funds-sent-to-a-previously-created-Bitkey-address")
        },
        onBack = { screenState = ScreenState.ShowingSweepState }
      )
      is ScreenState.ShowingLearnMore -> {
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = uiState.urlString,
              onClose = { screenState = ScreenState.ShowingHelpText }
            )
          }
        ).asModalScreen()
      }
      is ScreenState.ShowingMultipleTransactionsWarning -> {
        multipleTransactionsWarningScreenModel(
          id = when (props.sweepContext) {
            is SweepContext.Recovery -> when (props.sweepContext.recoveredFactor) {
              App -> DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_MULTIPLE_TRANSACTIONS_WARNING
              Hardware -> HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_MULTIPLE_TRANSACTIONS_WARNING
            }
            is SweepContext.PrivateWalletMigration, is SweepContext.W3Upgrade -> WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_MULTIPLE_TRANSACTIONS_WARNING
            else -> InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_MULTIPLE_TRANSACTIONS_WARNING
          },
          transactionCount = uiState.psbtsToSign.size,
          onContinue = {
            screenState = ScreenState.SigningSinglePsbt(
              currentPsbt = uiState.psbtsToSign.first(),
              remainingPsbts = uiState.psbtsToSign.drop(1),
              signedPsbts = emptySet(),
              sweepData = uiState.sweepData
            )
          },
          onBack = {
            uiState.sweepData.cancelHwSign()
            screenState = ScreenState.ShowingSweepState
          },
          presentationStyle = props.presentationStyle
        )
      }
      is ScreenState.SigningSinglePsbt -> {
        signingSinglePsbtScreen(
          props = props,
          state = uiState,
          setState = { screenState = it }
        )
      }
      is ScreenState.ReadyToSignNextPsbt -> {
        // Immediately transition to signing the next PSBT
        // This state exists to break out of the previous NFC session's composition
        LaunchedEffect(uiState) {
          screenState = ScreenState.SigningSinglePsbt(
            currentPsbt = uiState.currentPsbt,
            remainingPsbts = uiState.remainingPsbts,
            signedPsbts = uiState.signedPsbts,
            sweepData = uiState.sweepData
          )
        }
        // Show a brief loading screen during the transition
        generatingPsbtsLoadingScreen(props)
      }
    }
  }

  @Composable
  private fun getSweepScreen(
    props: SweepUiProps,
    sweepData: SweepData,
    setState: (ScreenState) -> Unit,
    initiatedSigningForData: AwaitingHardwareSignedSweepsData?,
    onInitiateSigning: (AwaitingHardwareSignedSweepsData) -> Unit,
  ): ScreenModel {
    // TODO: Add Hardware Proof of Possession state machine if GetAccountKeysets
    //   endpoint ends up requiring it.

    return when (sweepData) {
      /** Show spinner while we wait for PSBTs to be generated */
      is GeneratingPsbtsData ->
        generatingPsbtsLoadingScreen(props)

      /** Terminal error state: PSBT generation failed */
      is GeneratePsbtsFailedData ->
        generatePsbtsFailedScreenModel(
          id = when (props.sweepContext) {
            is SweepContext.Recovery -> when (props.sweepContext.recoveredFactor) {
              App -> DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_GENERATE_PSBTS_ERROR
              Hardware -> HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_GENERATE_PSBTS_ERROR
            }
            is SweepContext.PrivateWalletMigration, is SweepContext.W3Upgrade -> WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_GENERATING_PSBTS_ERROR
            else -> InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_GENERATE_PSBTS_ERROR
          },
          onPrimaryButtonClick = props.onExit ?: { sweepData.retry() },
          presentationStyle = props.presentationStyle
        )

      is NoFundsFoundData ->
        zeroBalancePrompt(
          id = when (props.sweepContext) {
            is SweepContext.Recovery -> when (props.sweepContext.recoveredFactor) {
              App -> DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_ZERO_BALANCE
              Hardware -> HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_ZERO_BALANCE
            }
            is SweepContext.PrivateWalletMigration, is SweepContext.W3Upgrade -> WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_ZERO_BALANCE
            else -> InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_ZERO_BALANCE
          },
          onDone = sweepData.proceed,
          presentationStyle = props.presentationStyle,
          eyebrow = if (props.sweepContext is SweepContext.W3Upgrade) "Step 4 of 4" else null
        )

      /** PSBTs have been generated. Prompt to continue to sign + broadcast. */
      is PsbtsGeneratedData -> {
        var showingNetworkFeesInfo by remember { mutableStateOf(false) }
        val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()

        when (props.sweepContext) {
          // For private wallet migration and W3 upgrade, use TransferConfirmationScreenModel
          is SweepContext.PrivateWalletMigration, is SweepContext.W3Upgrade -> {
            val transactionDetails = transactionDetailFromSweepData(
              totalTransferAmount = sweepData.totalTransferAmount,
              totalFeeAmount = sweepData.totalFeeAmount,
              fiatCurrency = fiatCurrency
            )
            ScreenModel(
              body = TransferConfirmationScreenModel(
                onBack = props.onExit ?: {
                  setState(ScreenState.ShowingSweepState)
                },
                variant = if (props.sweepContext is SweepContext.W3Upgrade) {
                  TransferConfirmationScreenVariant.W3Upgrade
                } else {
                  TransferConfirmationScreenVariant.PrivateWalletMigration
                },
                recipientAddress = BitcoinAddress(sweepData.destinationAddress),
                transactionDetails = transactionDetails,
                requiresHardware = true,
                confirmButtonEnabled = true,
                onConfirmClick = sweepData.startSweep,
                onNetworkFeesClick = { showingNetworkFeesInfo = true },
                onArrivalTimeClick = null,
                requiresHardwareReview = false
              ),
              bottomSheetModel = if (showingNetworkFeesInfo) {
                SheetModel(
                  body = NetworkFeesInfoSheetModel(onBack = { showingNetworkFeesInfo = false }),
                  onClosed = { showingNetworkFeesInfo = false }
                )
              } else {
                null
              },
              presentationStyle = props.presentationStyle
            )
          }
          is SweepContext.Recovery, SweepContext.InactiveWallet -> {
            val promptContext = when (props.sweepContext) {
              is SweepContext.Recovery -> SweepFundsPromptContext.Recovery(props.sweepContext.recoveredFactor)
              is SweepContext.InactiveWallet -> SweepFundsPromptContext.InactiveWallet
              is SweepContext.PrivateWalletMigration, is SweepContext.W3Upgrade -> error("Migration should use TransferConfirmationScreenModel")
            }

            sweepFundsPrompt(
              id = when (props.sweepContext) {
                is SweepContext.Recovery -> when (props.sweepContext.recoveredFactor) {
                  App -> DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT
                  Hardware -> HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT
                }
                is SweepContext.PrivateWalletMigration, is SweepContext.W3Upgrade -> WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_SIGN_PSBTS_PROMPT
                else -> InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_SIGN_PSBTS_PROMPT
              },
              fee = moneyAmountUiStateMachine.model(
                MoneyAmountUiProps(
                  primaryMoney = sweepData.totalFeeAmount,
                  secondaryAmountCurrency = fiatCurrency
                )
              ),
              transferAmount = moneyAmountUiStateMachine.model(
                MoneyAmountUiProps(
                  primaryMoney = sweepData.totalTransferAmount,
                  secondaryAmountCurrency = fiatCurrency
                )
              ),
              onBack = when (props.sweepContext) {
                is SweepContext.Recovery -> null
                else -> props.onExit
              },
              onHelpClick = {
                setState(ScreenState.ShowingHelpText)
              },
              onShowNetworkFeesInfo = { showingNetworkFeesInfo = true },
              onCloseNetworkFeesInfo = { showingNetworkFeesInfo = false },
              showNetworkFeesInfoSheet = showingNetworkFeesInfo,
              onSubmit = sweepData.startSweep,
              sweepContext = promptContext,
              presentationStyle = props.presentationStyle
            )
          }
        }
      }

      is AwaitingHardwareSignedSweepsData -> {
        // Start signing the first PSBT - transition to sequential signing state
        val psbtsToSign = sweepData.needsHwSign.toList()
        val isW3 = props.account.keybox.config.isW3Hardware
        val hasMultiplePsbts = psbtsToSign.size > 1

        // Only initiate signing if we haven't already for this data instance
        // This prevents re-triggering when returning to ShowingSweepState with stale data
        val shouldInitiateSigning = psbtsToSign.isNotEmpty() && initiatedSigningForData !== sweepData

        if (shouldInitiateSigning) {
          LaunchedEffect(sweepData) {
            onInitiateSigning(sweepData)
            // Show warning screen first if W3 with multiple PSBTs
            if (isW3 && hasMultiplePsbts) {
              setState(
                ScreenState.ShowingMultipleTransactionsWarning(
                  psbtsToSign = psbtsToSign,
                  sweepData = sweepData
                )
              )
            } else {
              setState(
                ScreenState.SigningSinglePsbt(
                  currentPsbt = psbtsToSign.first(),
                  remainingPsbts = psbtsToSign.drop(1),
                  signedPsbts = emptySet(),
                  sweepData = sweepData
                )
              )
            }
          }
        }
        // Show a loading screen while we transition
        generatingPsbtsLoadingScreen(props)
      }

      /** Server+App signing and broadcasting the transactions */
      is SigningAndBroadcastingSweepsData ->
        broadcastingScreenModel(
          id = when (props.sweepContext) {
            is SweepContext.Recovery -> when (props.sweepContext.recoveredFactor) {
              App -> DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_BROADCASTING
              Hardware -> HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_BROADCASTING
            }
            is SweepContext.PrivateWalletMigration, is SweepContext.W3Upgrade -> WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_BROADCASTING
            else -> InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_BROADCASTING
          },
          context = props.sweepContext,
          onBack = props.onExit ?: {
            logDebug { "Back not available" }
          },
          presentationStyle = props.presentationStyle
        )

      /** Terminal state: Broadcast completed */
      is SweepCompleteData ->
        when (props.sweepContext) {
          is SweepContext.PrivateWalletMigration, is SweepContext.W3Upgrade -> {
            val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()

            val transactionDetails = transactionDetailFromSweepData(
              totalTransferAmount = sweepData.totalTransferAmount,
              totalFeeAmount = sweepData.totalFeeAmount,
              fiatCurrency = fiatCurrency
            )

            TransferInitiatedBodyModel(
              onBack = {}, // can't go back here
              recipientAddress = BitcoinAddress(sweepData.destinationAddress),
              transactionDetails = transactionDetails,
              primaryButtonText = "Done",
              eventTrackerScreenId = WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_SUCCESS,
              onDone = sweepData.proceed
            ).asScreen(props.presentationStyle)
          }
          else ->
            sweepSuccessScreenModel(
              id = when (props.sweepContext) {
                is SweepContext.Recovery -> when (props.sweepContext.recoveredFactor) {
                  App -> DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SUCCESS
                  Hardware -> HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_SUCCESS
                }
                is SweepContext.PrivateWalletMigration, is SweepContext.W3Upgrade -> WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_SUCCESS
                else -> InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_SUCCESS
              },
              recoveredFactor = (props.sweepContext as? SweepContext.Recovery)?.recoveredFactor,
              onDone = sweepData.proceed,
              presentationStyle = props.presentationStyle
            )
        }

      is SweepCompleteNoData ->
        sweepSuccessScreenModel(
          id = when (props.sweepContext) {
            is SweepContext.Recovery -> when (props.sweepContext.recoveredFactor) {
              App -> DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SUCCESS
              Hardware -> HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_SUCCESS
            }
            is SweepContext.PrivateWalletMigration, is SweepContext.W3Upgrade -> WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_SUCCESS
            else -> InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_SUCCESS
          },
          recoveredFactor = (props.sweepContext as? SweepContext.Recovery)?.recoveredFactor,
          onDone = sweepData.proceed,
          presentationStyle = props.presentationStyle
        )

      /** Terminal error state: Sweep failed */
      is SweepFailedData ->
        sweepFailedScreenModel(
          id = when (props.sweepContext) {
            is SweepContext.Recovery -> when (props.sweepContext.recoveredFactor) {
              App -> DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_FAILED
              Hardware -> HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_FAILED
            }
            is SweepContext.PrivateWalletMigration, is SweepContext.W3Upgrade -> WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_FAILED
            else -> InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_FAILED
          },
          onRetry = sweepData.retry,
          onExit = props.onExit ?: { sweepData.retry() },
          presentationStyle = props.presentationStyle,
          errorData = ErrorData(
            segment = when (props.sweepContext) {
              is SweepContext.Recovery -> when (props.sweepContext.recoveredFactor) {
                App -> RecoverySegment.DelayAndNotify.LostApp.Completion
                Hardware -> RecoverySegment.DelayAndNotify.LostApp.Completion
              }
              is SweepContext.PrivateWalletMigration, is SweepContext.W3Upgrade -> PrivateWalletMigrationAppSegment.Sweep
              else -> RecoverySegment.AdditionalSweep.Sweep
            },
            actionDescription = "Sweeping funds",
            cause = sweepData.cause
          )
        )
    }
  }

  /**
   * Screen model for signing a single PSBT via NFC.
   * Delegates to [SignTransactionNfcSessionUiStateMachine] which handles the full NFC session
   * including W3 two-tap confirmation flow, progress tracking, and streaming signing.
   */
  @Composable
  private fun signingSinglePsbtScreen(
    props: SweepUiProps,
    state: ScreenState.SigningSinglePsbt,
    setState: (ScreenState) -> Unit,
  ): ScreenModel {
    return signTransactionNfcSessionUiStateMachine.model(
      SignTransactionNfcSessionUiProps(
        account = props.account,
        psbt = state.currentPsbt.psbt,
        spendingKeyset = state.currentPsbt.sourceKeyset,
        useRecoveryHwAuthKey = props.sweepContext is SweepContext.Recovery,
        // During W3 upgrade, the active keybox has been switched to the new W3 but the
        // sweep needs to sign with the OLD W1 hardware — skip pairing check to avoid
        // the W1's auth key being verified against the W3's auth key.
        skipPairingCheck = props.sweepContext is SweepContext.W3Upgrade,
        // During W3 upgrade, the final sweep tap intentionally uses the old W1 hardware.
        // Skip firmware telemetry so that tap does not overwrite the paired W3 device info.
        skipFirmwareTelemetry = props.sweepContext is SweepContext.W3Upgrade,
        // During W3 upgrade, the keybox has already been switched to W3
        // but the sweep needs to tap the OLD W1 hardware to sign transactions.
        hardwareTypeOverride = if (props.sweepContext is SweepContext.W3Upgrade) {
          HardwareType.W1
        } else {
          null
        },
        onBack = {
          // User cancelled - go back to the sweep state
          state.sweepData.cancelHwSign()
          setState(ScreenState.ShowingSweepState)
        },
        onSuccess = { signedPsbt ->
          val newSignedPsbts = state.signedPsbts + signedPsbt

          if (state.remainingPsbts.isEmpty()) {
            // All PSBTs have been signed - complete the flow
            // This will transition sweepData to SigningAndBroadcastingSweepsData
            state.sweepData.addHwSignedSweeps(newSignedPsbts)
            // Go back to ShowingSweepState so getSweepScreen renders the new sweepData state
            setState(ScreenState.ShowingSweepState)
          } else {
            // More PSBTs to sign - transition to intermediate state to break out of NFC session
            setState(
              ScreenState.ReadyToSignNextPsbt(
                currentPsbt = state.remainingPsbts.first(),
                remainingPsbts = state.remainingPsbts.drop(1),
                signedPsbts = newSignedPsbts,
                sweepData = state.sweepData
              )
            )
          }
        },
        showNativeSheetOnIos = false,
        eventTrackerContext = NfcEventTrackerScreenIdContext.SIGN_MANY_TRANSACTIONS
      )
    )
  }

  /**
   * Helper function to create the loading screen shown while generating PSBTs.
   * This is used in multiple places during the sweep flow.
   */
  private fun generatingPsbtsLoadingScreen(props: SweepUiProps): ScreenModel {
    return generatingPsbtsBodyModel(
      id = when (props.sweepContext) {
        is SweepContext.Recovery -> when (props.sweepContext.recoveredFactor) {
          App -> DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
          Hardware -> HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
        }
        is SweepContext.PrivateWalletMigration, is SweepContext.W3Upgrade -> WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_GENERATING_PSBTS
        else -> InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_GENERATING_PSBTS
      },
      onBack = props.onExit,
      presentationStyle = props.presentationStyle
    )
  }

  @Composable
  private fun transactionDetailFromSweepData(
    totalFeeAmount: BitcoinMoney,
    totalTransferAmount: BitcoinMoney,
    fiatCurrency: FiatCurrency,
  ): TransactionDetailsModel {
    val transferAmount = moneyAmountUiStateMachine.model(
      MoneyAmountUiProps(
        primaryMoney = totalTransferAmount,
        secondaryAmountCurrency = fiatCurrency
      )
    )
    val feeAmount = moneyAmountUiStateMachine.model(
      MoneyAmountUiProps(
        primaryMoney = totalFeeAmount,
        secondaryAmountCurrency = fiatCurrency
      )
    )
    val totalAmount = moneyAmountUiStateMachine.model(
      MoneyAmountUiProps(
        primaryMoney = totalTransferAmount + totalFeeAmount,
        secondaryAmountCurrency = fiatCurrency
      )
    )
    return TransactionDetailsModel(
      transactionSpeedText = "~30 minutes",
      transactionDetailModelType = TransactionDetailModelType.Regular(
        transferAmountText = transferAmount.primaryAmount,
        transferAmountSecondaryText = transferAmount.secondaryAmount,
        feeAmountText = feeAmount.primaryAmount,
        feeAmountSecondaryText = feeAmount.secondaryAmount,
        totalAmountPrimaryText = totalAmount.primaryAmount,
        totalAmountSecondaryText = totalAmount.secondaryAmount
      )
    )
  }

  private sealed interface ScreenState {
    /**
     * Currently displaying a screen based on the [SweepData]
     */
    data object ShowingSweepState : ScreenState

    /**
     * Displaying help text to explain why a sweep is required
     */
    data object ShowingHelpText : ScreenState

    /**
     * Displaying 'learn more' help center article
     */
    data class ShowingLearnMore(
      val urlString: String,
    ) : ScreenState

    /**
     * Showing warning about multiple transactions to sign on W3 hardware.
     */
    data class ShowingMultipleTransactionsWarning(
      val psbtsToSign: List<SweepPsbt>,
      val sweepData: AwaitingHardwareSignedSweepsData,
    ) : ScreenState

    /**
     * Signing a single PSBT via NFC. Delegates to [SignTransactionNfcSessionUiStateMachine]
     * which handles the full NFC session including W3 two-tap confirmation and progress.
     */
    data class SigningSinglePsbt(
      val currentPsbt: SweepPsbt,
      val remainingPsbts: List<SweepPsbt>,
      val signedPsbts: Set<Psbt>,
      val sweepData: AwaitingHardwareSignedSweepsData,
    ) : ScreenState

    /**
     * Ready to sign the next PSBT. This intermediate state exists to break out of the
     * previous NFC session's composition before starting a new one.
     */
    data class ReadyToSignNextPsbt(
      val currentPsbt: SweepPsbt,
      val remainingPsbts: List<SweepPsbt>,
      val signedPsbts: Set<Psbt>,
      val sweepData: AwaitingHardwareSignedSweepsData,
    ) : ScreenState
  }
}
