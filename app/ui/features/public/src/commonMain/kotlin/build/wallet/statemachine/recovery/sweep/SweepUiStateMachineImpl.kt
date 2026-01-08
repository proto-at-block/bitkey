package build.wallet.statemachine.recovery.sweep

import androidx.compose.runtime.*
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
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.recovery.sweep.SweepContext
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
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.Required
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.send.*
import build.wallet.statemachine.walletmigration.PrivateWalletMigrationAppSegment

@BitkeyInject(ActivityScope::class)
class SweepUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
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
        keybox = props.keybox,
        sweepContext = props.sweepContext,
        onSuccess = props.onSuccess,
        onAttemptSweep = props.onAttemptSweep
      )
    )

    return when (val uiState = screenState) {
      ScreenState.ShowingSweepState -> getSweepScreen(props, sweepData, setState = { screenState = it })
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
    }
  }

  @Composable
  private fun getSweepScreen(
    props: SweepUiProps,
    sweepData: SweepData,
    setState: (ScreenState) -> Unit,
  ): ScreenModel {
    // TODO: Add Hardware Proof of Possession state machine if GetAccountKeysets
    //   endpoint ends up requiring it.

    return when (sweepData) {
      /** Show spinner while we wait for PSBTs to be generated */
      is GeneratingPsbtsData ->
        generatingPsbtsBodyModel(
          id = when (props.sweepContext) {
            is SweepContext.Recovery -> when (props.sweepContext.recoveredFactor) {
              App -> DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
              Hardware -> HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
            }
            is SweepContext.PrivateWalletMigration -> WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_GENERATING_PSBTS
            else -> InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_GENERATING_PSBTS
          },
          onBack = props.onExit,
          presentationStyle = props.presentationStyle
        )

      /** Terminal error state: PSBT generation failed */
      is GeneratePsbtsFailedData ->
        generatePsbtsFailedScreenModel(
          id = when (props.sweepContext) {
            is SweepContext.Recovery -> when (props.sweepContext.recoveredFactor) {
              App -> DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_GENERATE_PSBTS_ERROR
              Hardware -> HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_GENERATE_PSBTS_ERROR
            }
            is SweepContext.PrivateWalletMigration -> WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_GENERATING_PSBTS_ERROR
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
            is SweepContext.PrivateWalletMigration -> WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_ZERO_BALANCE
            else -> InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_ZERO_BALANCE
          },
          onDone = sweepData.proceed,
          presentationStyle = props.presentationStyle
        )

      /** PSBTs have been generated. Prompt to continue to sign + broadcast. */
      is PsbtsGeneratedData -> {
        var showingNetworkFeesInfo by remember { mutableStateOf(false) }
        val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()

        when (props.sweepContext) {
          // For private wallet migration, use TransferConfirmationScreenModel
          is SweepContext.PrivateWalletMigration -> {
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
                variant = TransferConfirmationScreenVariant.PrivateWalletMigration,
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
              is SweepContext.PrivateWalletMigration -> error("PrivateWalletMigration should use TransferConfirmationScreenModel")
            }

            sweepFundsPrompt(
              id = when (props.sweepContext) {
                is SweepContext.Recovery -> when (props.sweepContext.recoveredFactor) {
                  App -> DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT
                  Hardware -> HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT
                }
                is SweepContext.PrivateWalletMigration -> WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_SIGN_PSBTS_PROMPT
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

      is AwaitingHardwareSignedSweepsData ->
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              sweepData.needsHwSign
                .mapNotNull {
                  val result = commands.signTransaction(session, it.psbt, it.sourceKeyset)
                  when (result) {
                    is HardwareInteraction.Completed<Psbt> -> result.result
                    else -> null
                  }
                }
                .toSet()
            },
            onSuccess = sweepData.addHwSignedSweeps,
            onCancel = props.onExit ?: {
              logDebug { "Back not available" }
            },
            screenPresentationStyle = props.presentationStyle,
            eventTrackerContext = NfcEventTrackerScreenIdContext.SIGN_MANY_TRANSACTIONS,
            hardwareVerification = Required(useRecoveryPubKey = props.sweepContext is SweepContext.Recovery),
            shouldShowLongRunningOperation = true
          )
        )

      /** Server+App signing and broadcasting the transactions */
      is SigningAndBroadcastingSweepsData ->
        broadcastingScreenModel(
          id = when (props.sweepContext) {
            is SweepContext.Recovery -> when (props.sweepContext.recoveredFactor) {
              App -> DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_BROADCASTING
              Hardware -> HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_BROADCASTING
            }
            is SweepContext.PrivateWalletMigration -> WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_BROADCASTING
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
          is SweepContext.PrivateWalletMigration -> {
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
              shouldTrack = true,
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
                is SweepContext.PrivateWalletMigration -> WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_SUCCESS
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
            is SweepContext.PrivateWalletMigration -> WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_SUCCESS
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
            is SweepContext.PrivateWalletMigration -> WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_FAILED
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
              is SweepContext.PrivateWalletMigration -> PrivateWalletMigrationAppSegment.Sweep
              else -> RecoverySegment.AdditionalSweep.Sweep
            },
            actionDescription = "Sweeping funds",
            cause = sweepData.cause
          )
        )
    }
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
  }
}
