package build.wallet.statemachine.moneyhome.full

import androidx.compose.runtime.*
import bitkey.recovery.RecoveryStatusService
import bitkey.ui.framework.NavigatorPresenter
import build.wallet.activity.Transaction
import build.wallet.analytics.events.screen.id.MoneyHomeEventTrackerScreenId.MONEY_HOME_ALL_TRANSACTIONS
import build.wallet.bitcoin.invoice.ParsedPaymentData
import build.wallet.bitcoin.invoice.PaymentDataParser
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.inheritance.InheritanceClaimId
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.bitkey.relationships.TrustedContact
import build.wallet.compose.collections.buildImmutableList
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.fwup.FirmwareData
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.onboarding.OnboardingCompletionService
import build.wallet.partnerships.*
import build.wallet.platform.clipboard.Clipboard
import build.wallet.platform.links.DeepLinkHandler
import build.wallet.platform.links.OpenDeeplinkResult
import build.wallet.platform.links.OpenDeeplinkResult.AppRestrictionResult.*
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.pricechart.ChartType
import build.wallet.recovery.Recovery.StillRecovering.ServerDependentRecovery.InitiatedRecovery
import build.wallet.recovery.Recovery.StillRecovering.ServerIndependentRecovery
import build.wallet.recovery.socrec.PostSocRecTaskRepository
import build.wallet.recovery.socrec.SocRecService
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.list.ListFormBodyModel
import build.wallet.statemachine.fwup.FwupScreen
import build.wallet.statemachine.inheritance.DeclineInheritanceClaimUiProps
import build.wallet.statemachine.inheritance.DeclineInheritanceClaimUiStateMachine
import build.wallet.statemachine.inheritance.claims.complete.CompleteInheritanceClaimUiStateMachine
import build.wallet.statemachine.inheritance.claims.complete.CompleteInheritanceClaimUiStateMachineProps
import build.wallet.statemachine.limit.SetSpendingLimitUiStateMachine
import build.wallet.statemachine.limit.SpendingLimitProps
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.*
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingBalanceUiState.BottomSheetDisplayState.Partners
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingTransactionUiState.EntryPoint
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingTransactionUiState.EntryPoint.ACTIVITY
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingTransactionUiState.EntryPoint.BALANCE
import build.wallet.statemachine.partnerships.AddBitcoinBottomSheetDisplayState
import build.wallet.statemachine.partnerships.purchase.CustomAmountEntryUiProps
import build.wallet.statemachine.partnerships.purchase.CustomAmountEntryUiStateMachine
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseQuotesUiProps
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseQuotesUiStateMachine
import build.wallet.statemachine.partnerships.sell.ConfirmedPartnerSale
import build.wallet.statemachine.partnerships.sell.PartnershipsSellUiProps
import build.wallet.statemachine.partnerships.sell.PartnershipsSellUiStateMachine
import build.wallet.statemachine.pricechart.BitcoinPriceChartUiProps
import build.wallet.statemachine.pricechart.BitcoinPriceChartUiStateMachine
import build.wallet.statemachine.receive.AddressQrCodeUiProps
import build.wallet.statemachine.receive.AddressQrCodeUiStateMachine
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryProps
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryUiStateMachine
import build.wallet.statemachine.recovery.losthardware.initiate.InstructionsStyle
import build.wallet.statemachine.recovery.sweep.SweepUiProps
import build.wallet.statemachine.recovery.sweep.SweepUiStateMachine
import build.wallet.statemachine.send.SendUiProps
import build.wallet.statemachine.send.SendUiStateMachine
import build.wallet.statemachine.transactions.*
import build.wallet.statemachine.transactions.TransactionsActivityProps.TransactionVisibility.All
import build.wallet.statemachine.utxo.UtxoConsolidationProps
import build.wallet.statemachine.utxo.UtxoConsolidationUiStateMachine
import build.wallet.statemachine.walletmigration.PrivateWalletMigrationUiProps
import build.wallet.statemachine.walletmigration.PrivateWalletMigrationUiStateMachine
import build.wallet.time.nonNegativeDurationBetween
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.wallet.migration.PrivateWalletMigrationService
import build.wallet.wallet.migration.PrivateWalletMigrationState
import build.wallet.wallet.migration.PrivateWalletMigrationState.NotAvailable
import com.github.michaelbull.result.get
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.time.Duration

@BitkeyInject(ActivityScope::class)
class MoneyHomeUiStateMachineImpl(
  private val addressQrCodeUiStateMachine: AddressQrCodeUiStateMachine,
  private val sendUiStateMachine: SendUiStateMachine,
  private val transactionDetailsUiStateMachine: TransactionDetailsUiStateMachine,
  private val transactionsActivityUiStateMachine: TransactionsActivityUiStateMachine,
  private val lostHardwareUiStateMachine: LostHardwareRecoveryUiStateMachine,
  private val setSpendingLimitUiStateMachine: SetSpendingLimitUiStateMachine,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val clipboard: Clipboard,
  private val paymentDataParser: PaymentDataParser,
  private val recoveryIncompleteRepository: PostSocRecTaskRepository,
  private val moneyHomeViewingBalanceUiStateMachine: MoneyHomeViewingBalanceUiStateMachine,
  private val customAmountEntryUiStateMachine: CustomAmountEntryUiStateMachine,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val sweepUiStateMachine: SweepUiStateMachine,
  private val bitcoinPriceChartUiStateMachine: BitcoinPriceChartUiStateMachine,
  private val socRecService: SocRecService,
  private val utxoConsolidationUiStateMachine: UtxoConsolidationUiStateMachine,
  private val partnershipsSellUiStateMachine: PartnershipsSellUiStateMachine,
  private val failedPartnerTransactionUiStateMachine: FailedPartnerTransactionUiStateMachine,
  private val completeClaimUiStateMachine: CompleteInheritanceClaimUiStateMachine,
  private val declineInheritanceClaimUiStateMachine: DeclineInheritanceClaimUiStateMachine,
  private val onboardingCompletionService: OnboardingCompletionService,
  private val navigatorPresenter: NavigatorPresenter,
  private val privateWalletMigrationService: PrivateWalletMigrationService,
  private val privateWalletMigrationUiStateMachine: PrivateWalletMigrationUiStateMachine,
  private val recoveryStatusService: RecoveryStatusService,
  private val clock: Clock,
  private val partnershipsPurchaseQuotesUiStateMachine: PartnershipsPurchaseQuotesUiStateMachine,
  private val deepLinkHandler: DeepLinkHandler,
) : MoneyHomeUiStateMachine {
  @Composable
  override fun model(props: MoneyHomeUiProps): ScreenModel {
    val justCompletingSocialRecovery by remember {
      socRecService.justCompletedRecovery()
    }.collectAsState(initial = false)

    val migrationState by privateWalletMigrationService.migrationState.collectAsState(NotAvailable)

    var hasAutoShownSocialRecoveryScreen by remember { mutableStateOf(false) }

    val isCompletingRecovery by remember {
      derivedStateOf {
        when (val recovery = recoveryStatusService.status.value) {
          is InitiatedRecovery -> {
            val remainingDelayPeriod = nonNegativeDurationBetween(
              startTime = clock.now(),
              endTime = recovery.serverRecovery.delayEndTime
            )
            remainingDelayPeriod == Duration.ZERO && recovery.factorToRecover == PhysicalFactor.Hardware
          }
          is ServerIndependentRecovery -> recovery.factorToRecover == PhysicalFactor.Hardware
          else -> false
        }
      }
    }

    LaunchedEffect("mark-onboarding-completed") {
      // Ensure onboarding is recorded for users who completed it before
      // this feature was introduced
      onboardingCompletionService.recordCompletionIfNotExists()
    }

    var uiState: MoneyHomeUiState by remember(
      props.origin,
      justCompletingSocialRecovery,
      migrationState,
      isCompletingRecovery
    ) {
      val initialState = when (val origin = props.origin) {
        MoneyHomeUiProps.Origin.Launch -> {
          // Navigate directly to hardware recovery when completing hardware recovery
          when {
            migrationState is PrivateWalletMigrationState.InProgress -> PrivateWalletMigrationUiState(
              inProgress = true
            )
            justCompletingSocialRecovery && !hasAutoShownSocialRecoveryScreen -> {
              hasAutoShownSocialRecoveryScreen = true
              ViewHardwareRecoveryStatusUiState(InstructionsStyle.ContinuingRecovery)
            }
            isCompletingRecovery -> ViewHardwareRecoveryStatusUiState(InstructionsStyle.Independent)
            else -> ViewingBalanceUiState()
          }
        }
        is MoneyHomeUiProps.Origin.PartnershipsSell -> {
          ConfirmingPartnerSale(
            origin.partnerId,
            origin.event,
            origin.partnerTransactionId
          )
        }
        is MoneyHomeUiProps.Origin.LostHardwareRecovery -> {
          ViewHardwareRecoveryStatusUiState(
            instructionsStyle = when {
              isCompletingRecovery || !origin.isContinuingRecovery -> InstructionsStyle.Independent
              else -> InstructionsStyle.ResumedRecoveryAttempt
            }
          )
        }
        is MoneyHomeUiProps.Origin.PartnershipTransferLink -> {
          ViewingBalanceUiState(
            partnerTransferLinkRequest = origin.request
          )
        }
        else -> ViewingBalanceUiState()
      }
      mutableStateOf(initialState)
    }

    return when (val state = uiState) {
      is ViewingBalanceUiState -> moneyHomeViewingBalanceUiStateMachine.model(
        props = MoneyHomeViewingBalanceUiProps(
          account = props.account,
          homeStatusBannerModel = props.homeStatusBannerModel,
          onSettings = props.onSettings,
          state = state,
          setState = { uiState = it },
          onPartnershipsWebFlowCompleted = props.onPartnershipsWebFlowCompleted,
          onStartSweepFlow = {
            uiState = PerformingSweep
          },
          onGoToSecurityHub = props.onGoToSecurityHub,
          onGoToPrivateWalletMigration = {
            uiState = PrivateWalletMigrationUiState()
          },
          onPurchaseAmountConfirmed = { amount ->
            uiState = ViewingPartnerPurchaseQuotesUiState(purchaseAmount = amount)
          }
        )
      )

      is PerformingSweep -> performSweepModel(
        account = props.account as FullAccount,
        onExit = { uiState = ViewingBalanceUiState() },
        onSuccess = { uiState = ViewingBalanceUiState() }
      )

      SellFlowUiState -> partnershipsSellUiStateMachine.model(
        props = PartnershipsSellUiProps(
          onBack = { uiState = ViewingBalanceUiState() }
        )
      )

      ReceiveFlowUiState -> ReceiveBitcoinModel(
        props,
        onWebLinkOpened = { url, info, transaction ->
          uiState = ShowingInAppBrowserUiState(
            urlString = url,
            onClose = {
              props.onPartnershipsWebFlowCompleted(info, transaction)
            }
          )
        },
        onExit = {
          uiState = ViewingBalanceUiState()
        }
      )

      is SendFlowUiState -> SendBitcoinModel(
        validPaymentDataInClipboard =
          clipboard.getPlainTextItem()?.let {
            paymentDataParser.decode(
              paymentDataString = it.data,
              networkType = props.account.config.bitcoinNetworkType
            ).get()
          },
        onExit = {
          uiState = ViewingBalanceUiState()
        },
        onGoToUtxoConsolidation = {
          uiState = ConsolidatingUtxosUiState
        }
      )

      SetSpendingLimitFlowUiState -> SetSpendingLimitModel(
        props,
        onExit = {
          uiState = ViewingBalanceUiState()
        }
      )

      is ViewHardwareRecoveryStatusUiState -> HardwareRecoveryModel(
        account = props.account as FullAccount,
        instructionsStyle = state.instructionsStyle,
        onExit = {
          uiState = ViewingBalanceUiState()
          props.onDismissOrigin()
        }
      )

      is FwupFlowUiState -> navigatorPresenter.model(
        initialScreen = FwupScreen(
          firmwareUpdateData = state.firmwareData,
          onExit = { uiState = ViewingBalanceUiState() }
        ),
        onExit = { uiState = ViewingBalanceUiState() }
      )

      ViewingAllTransactionActivityUiState -> {
        ListFormBodyModel(
          toolbarTitle = "Activity",
          listGroups = buildImmutableList {
            transactionsActivityUiStateMachine.model(
              props = TransactionsActivityProps(
                transactionVisibility = All,
                onTransactionClicked = { transaction ->
                  uiState = ViewingTransactionUiState(
                    transaction = transaction,
                    entryPoint = ACTIVITY
                  )
                }
              )
            )?.let { add(it.listModel) }
          },
          onBack = {
            uiState = ViewingBalanceUiState()
          },
          id = MONEY_HOME_ALL_TRANSACTIONS
        ).asRootScreen()
      }

      is ViewingTransactionUiState -> TransactionDetailsModel(
        props = props,
        state = state,
        onClose = { entryPoint ->
          uiState =
            when (entryPoint) {
              BALANCE -> ViewingBalanceUiState()
              ACTIVITY -> ViewingAllTransactionActivityUiState
            }
        }
      )

      is ShowingInAppBrowserUiState -> InAppBrowserModel(
        open = {
          inAppBrowserNavigator.open(
            url = state.urlString,
            onClose = state.onClose
          )
        }
      ).asModalScreen()

      is SelectCustomPartnerPurchaseAmountState -> {
        val fiatCurrency = fiatCurrencyPreferenceRepository.fiatCurrencyPreference.value
        customAmountEntryUiStateMachine.model(
          props = CustomAmountEntryUiProps(
            minimumAmount = state.minimumAmount,
            maximumAmount = state.maximumAmount,
            onBack = {
              uiState = ViewingBalanceUiState(
                bottomSheetDisplayState = Partners(
                  initialState = AddBitcoinBottomSheetDisplayState.PurchasingUiState(
                    selectedAmount = FiatMoney.zero(fiatCurrency)
                  )
                )
              )
            },
            onNext = { selectedAmount ->
              // Go directly to quotes flow with the custom amount
              uiState = ViewingPartnerPurchaseQuotesUiState(purchaseAmount = selectedAmount)
            }
          )
        )
      }

      is ShowingPriceChartUiState -> bitcoinPriceChartUiStateMachine.model(
        BitcoinPriceChartUiProps(
          initialType = state.type,
          onBuy = {
            uiState = ViewingBalanceUiState(
              bottomSheetDisplayState = Partners(
                AddBitcoinBottomSheetDisplayState.PurchasingUiState(selectedAmount = null)
              )
            )
          },
          onTransfer = { uiState = ReceiveFlowUiState },
          onBack = { uiState = ViewingBalanceUiState() }
        )
      )

      ConsolidatingUtxosUiState -> utxoConsolidationUiStateMachine.model(
        props = UtxoConsolidationProps(
          onConsolidationSuccess = {
            uiState = ViewingBalanceUiState()
          },
          onBack = { uiState = ViewingBalanceUiState() }
        )
      )
      is ConfirmingPartnerSale -> partnershipsSellUiStateMachine.model(
        props = PartnershipsSellUiProps(
          confirmedSale = ConfirmedPartnerSale(
            partner = state.partner,
            event = state.event,
            partnerTransactionId = state.partnerTransactionId
          ),
          onBack = { uiState = ViewingBalanceUiState() }
        )
      )

      is DenyInheritanceClaimUiState -> declineInheritanceClaimUiStateMachine.model(
        props = DeclineInheritanceClaimUiProps(
          fullAccount = props.account as FullAccount,
          claimId = state.claimId.value,
          onBack = { uiState = ViewingBalanceUiState() },
          onBeneficiaryRemoved = {
            uiState = ViewingBalanceUiState()
          },
          onClaimDeclined = { uiState = ViewingBalanceUiState() }
        )
      )

      is CompleteInheritanceClaimUiState -> completeClaimUiStateMachine.model(
        CompleteInheritanceClaimUiStateMachineProps(
          relationshipId = state.relationshipId,
          account = props.account as FullAccount,
          onExit = { uiState = ViewingBalanceUiState() }
        )
      )

      is PrivateWalletMigrationUiState -> privateWalletMigrationUiStateMachine.model(
        PrivateWalletMigrationUiProps(
          account = props.account as FullAccount,
          onMigrationComplete = { uiState = ViewingBalanceUiState() },
          onExit = { uiState = ViewingBalanceUiState() },
          inProgress = state.inProgress
        )
      )

      is ViewingPartnerPurchaseQuotesUiState -> ViewingPartnerPurchaseQuotesModel(
        props = props,
        state = state,
        setState = { uiState = it }
      )
    }
  }

  @Composable
  private fun ReceiveBitcoinModel(
    props: MoneyHomeUiProps,
    onWebLinkOpened: (String, PartnerInfo, PartnershipTransaction) -> Unit,
    onExit: () -> Unit,
  ): ScreenModel {
    return addressQrCodeUiStateMachine.model(
      props = AddressQrCodeUiProps(
        account = props.account as FullAccount,
        onWebLinkOpened = onWebLinkOpened,
        onBack = onExit
      )
    ).asModalFullScreen()
  }

  @Composable
  private fun SendBitcoinModel(
    validPaymentDataInClipboard: ParsedPaymentData?,
    onExit: () -> Unit,
    onGoToUtxoConsolidation: () -> Unit,
  ) = sendUiStateMachine.model(
    props = SendUiProps(
      validInvoiceInClipboard = validPaymentDataInClipboard,
      onExit = onExit,
      // Since hitting "Done" is the same as exiting out of the send flow.
      onDone = onExit,
      onGoToUtxoConsolidation = onGoToUtxoConsolidation
    )
  )

  @Composable
  private fun SetSpendingLimitModel(
    props: MoneyHomeUiProps,
    onExit: () -> Unit,
  ) = setSpendingLimitUiStateMachine.model(
    props = SpendingLimitProps(
      currentSpendingLimit = null,
      account = props.account as FullAccount,
      onClose = onExit,
      onSetLimit = { onExit() }
    )
  )

  @Composable
  private fun TransactionDetailsModel(
    props: MoneyHomeUiProps,
    state: ViewingTransactionUiState,
    onClose: (EntryPoint) -> Unit,
  ): ScreenModel {
    return when (state.transaction) {
      is Transaction.BitcoinWalletTransaction -> transactionDetailsUiStateMachine.model(
        props = TransactionDetailsUiProps(
          account = props.account,
          transaction = state.transaction,
          onClose = { onClose(state.entryPoint) }
        )
      )
      is Transaction.PartnershipTransaction -> if (state.transaction.details.status == PartnershipTransactionStatus.FAILED) {
        failedPartnerTransactionUiStateMachine.model(
          props = FailedPartnerTransactionProps(
            transaction = state.transaction,
            onClose = { onClose(state.entryPoint) }
          )
        )
      } else {
        transactionDetailsUiStateMachine.model(
          props = TransactionDetailsUiProps(
            account = props.account,
            transaction = state.transaction,
            onClose = { onClose(state.entryPoint) }
          )
        )
      }
    }
  }

  @Composable
  private fun performSweepModel(
    account: FullAccount,
    onExit: () -> Unit,
    onSuccess: () -> Unit,
  ): ScreenModel {
    return sweepUiStateMachine.model(
      SweepUiProps(
        hasAttemptedSweep = false,
        presentationStyle = ScreenPresentationStyle.ModalFullScreen,
        onExit = onExit,
        onSuccess = onSuccess,
        keybox = account.keybox,
        // This callback is used to update the RecoveryStatusService in the
        // recovery flow, and is not necessary in this context.
        onAttemptSweep = {}
      )
    )
  }

  @Composable
  private fun HardwareRecoveryModel(
    account: FullAccount,
    instructionsStyle: InstructionsStyle,
    onExit: () -> Unit,
  ): ScreenModel {
    val scope = rememberStableCoroutineScope()
    return lostHardwareUiStateMachine.model(
      props = LostHardwareRecoveryProps(
        account = account,
        onExit = onExit,
        onFoundHardware = {
          scope.launch {
            // Set the flag to no longer show the replace hardware card nudge
            // this flag is used by the MoneyHomeCardsUiStateMachine
            // and toggled on by the FullAccountCloudBackupRestorationUiStateMachine
            recoveryIncompleteRepository.setHardwareReplacementNeeded(false)
          }
          onExit()
        },
        screenPresentationStyle = Modal,
        instructionsStyle = instructionsStyle,
        onComplete = onExit
      )
    )
  }

  @Composable
  private fun ViewingPartnerPurchaseQuotesModel(
    props: MoneyHomeUiProps,
    state: ViewingPartnerPurchaseQuotesUiState,
    setState: (MoneyHomeUiState) -> Unit,
  ): ScreenModel {
    var alertModel: ButtonAlertModel? by remember { mutableStateOf(null) }

    val screenModel = partnershipsPurchaseQuotesUiStateMachine.model(
      props = PartnershipsPurchaseQuotesUiProps(
        purchaseAmount = state.purchaseAmount,
        onPartnerRedirected = { redirectMethod, transaction ->
          handlePartnerRedirected(
            method = redirectMethod,
            transaction = transaction,
            props = props,
            setState = setState,
            onShowAlert = { alertModel = it },
            onDismissAlert = { alertModel = null }
          )
        },
        onBack = {
          // Return to amount selection sheet
          setState(
            ViewingBalanceUiState(
              bottomSheetDisplayState = Partners(
                initialState = AddBitcoinBottomSheetDisplayState.PurchasingUiState(
                  selectedAmount = state.purchaseAmount
                )
              )
            )
          )
        },
        onExit = { setState(ViewingBalanceUiState()) }
      )
    )

    return screenModel.copy(alertModel = alertModel)
  }

  private fun handlePartnerRedirected(
    method: PartnerRedirectionMethod,
    transaction: PartnershipTransaction,
    props: MoneyHomeUiProps,
    setState: (MoneyHomeUiState) -> Unit,
    onShowAlert: (ButtonAlertModel) -> Unit,
    onDismissAlert: () -> Unit,
  ) {
    when (method) {
      is PartnerRedirectionMethod.Deeplink -> {
        val result = deepLinkHandler.openDeeplink(
          url = method.urlString,
          appRestrictions = method.appRestrictions
        )
        val alert: ButtonAlertModel? = when (result) {
          OpenDeeplinkResult.Failed ->
            ButtonAlertModel(
              title = "Failed to open ${method.partnerName}.",
              subline = null,
              onDismiss = onDismissAlert,
              primaryButtonText = "OK",
              onPrimaryButtonClick = onDismissAlert
            )

          is OpenDeeplinkResult.Opened ->
            when (result.appRestrictionResult) {
              is Failed ->
                ButtonAlertModel(
                  title = "The version of ${method.partnerName} may be out of date. Please update your app.",
                  subline = null,
                  onDismiss = onDismissAlert,
                  primaryButtonText = "OK",
                  onPrimaryButtonClick = onDismissAlert
                )

              None, Success -> null
            }
        }
        // Dismiss quotes screen and return to balance
        setState(ViewingBalanceUiState())

        // Show alert if there is one
        alert?.let { onShowAlert(it) }
      }

      is PartnerRedirectionMethod.Web -> {
        setState(
          ShowingInAppBrowserUiState(
            urlString = method.urlString,
            onClose = {
              props.onPartnershipsWebFlowCompleted(method.partnerInfo, transaction)
            }
          )
        )
      }
    }
  }
}

sealed interface MoneyHomeUiState {
  /**
   * Indicates that we are viewing the balance, with nothing presented on top
   */
  data class ViewingBalanceUiState(
    val isRefreshing: Boolean = false,
    val bottomSheetDisplayState: BottomSheetDisplayState? = null,
    val selectedContact: TrustedContact? = null,
    val partnerTransferLinkRequest: PartnerTransferLinkRequest? = null,
  ) : MoneyHomeUiState {
    sealed interface BottomSheetDisplayState {
      /**
       * We have entered the partners flow, which is a half-sheet
       * displayed on top of the money home screen
       */
      data class Partners(
        val initialState: AddBitcoinBottomSheetDisplayState,
      ) : BottomSheetDisplayState

      /**
       * Enabling mobile pay or optionally skipping - shown when user enters from getting
       * started
       *
       * @property skipped - when true, the db operation to mark the getting started task is executed
       */
      data class MobilePay(val skipped: Boolean) : BottomSheetDisplayState

      /**
       * Showing a bottom modal for the user to complete a fwup to access the feature
       * to add an additional fingerprint.
       */
      data object PromptingForFwUpUiState : BottomSheetDisplayState
    }
  }

  /**
   * Indicates that we are in the sell flow.
   */
  data object SellFlowUiState : MoneyHomeUiState

  /**
   * Indicates that we are in send flow.
   */
  data object SendFlowUiState : MoneyHomeUiState

  /**
   * Indicates that we are in the receive flow.
   */
  data object ReceiveFlowUiState : MoneyHomeUiState

  data object PerformingSweep : MoneyHomeUiState

  /**
   * Indicates that we are viewing the status of an active HW recovery
   */
  data class ViewHardwareRecoveryStatusUiState(
    val instructionsStyle: InstructionsStyle,
  ) : MoneyHomeUiState

  /**
   * Indicates that we are in the FWUP flow.
   */
  data class FwupFlowUiState(
    val firmwareData: FirmwareData.FirmwareUpdateState.PendingUpdate,
  ) : MoneyHomeUiState

  /**
   * Indicates that we are viewing details for the given transaction.
   */
  data class ViewingTransactionUiState(
    val transaction: Transaction,
    val entryPoint: EntryPoint,
  ) : MoneyHomeUiState {
    /** Where the details were launched from */
    enum class EntryPoint {
      BALANCE,
      ACTIVITY,
    }
  }

  /**
   * Indicates that we are viewing activity for all transactions.
   */
  data object ViewingAllTransactionActivityUiState : MoneyHomeUiState

  /**
   * Indicates that we are in the set spending limit flow.
   */
  data object SetSpendingLimitFlowUiState : MoneyHomeUiState

  /**
   * Indicates that we are displaying an in-app browser on top of Money Home
   *
   * @param urlString - url to kick off the in-app browser with.
   * @param onClose - callback fired when browser closes
   */
  data class ShowingInAppBrowserUiState(
    val urlString: String,
    val onClose: () -> Unit,
  ) : MoneyHomeUiState

  /**
   * Indicates that we are in the process of selecting a custom partner purchase amount
   * @param minimumAmount - the minimum amount that can be selected
   * @param maximumAmount - the maximum amount that can be selected
   */
  data class SelectCustomPartnerPurchaseAmountState(
    val minimumAmount: FiatMoney,
    val maximumAmount: FiatMoney,
  ) : MoneyHomeUiState

  /**
   * Displays various interactive price charts defaulting to [type].
   *
   * @param type The initial chart type to display.
   */
  data class ShowingPriceChartUiState(
    val type: ChartType = ChartType.BTC_PRICE,
  ) : MoneyHomeUiState

  data object ConsolidatingUtxosUiState : MoneyHomeUiState

  /**
   * Indicates that we are confirming a partner sale, usually from a deeplink
   *
   * @property partner - The id of the relevant partner
   * @property
   */
  data class ConfirmingPartnerSale(
    val partner: PartnerId?,
    val event: PartnershipEvent?,
    val partnerTransactionId: PartnershipTransactionId?,
  ) : MoneyHomeUiState

  /**
   * Deny inheritance claim flow, presented modally from the money home card for the benefactor
   */
  data class DenyInheritanceClaimUiState(
    val claimId: InheritanceClaimId,
  ) : MoneyHomeUiState

  /**
   * Complete inheritance claim flow, presented modally from the money home card for the beneficiary
   */
  data class CompleteInheritanceClaimUiState(
    val relationshipId: RelationshipId,
  ) : MoneyHomeUiState

  /**
   * Private wallet migration flow, presented modally from the coachmark
   */
  data class PrivateWalletMigrationUiState(
    val inProgress: Boolean = false,
  ) : MoneyHomeUiState

  /**
   * Indicates that we are viewing partner purchase quotes for a confirmed amount.
   */
  data class ViewingPartnerPurchaseQuotesUiState(
    val purchaseAmount: FiatMoney,
  ) : MoneyHomeUiState
}
