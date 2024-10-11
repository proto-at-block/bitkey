package build.wallet.statemachine.moneyhome.full

import androidx.compose.runtime.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action
import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Available
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.TransactionsData.LoadingTransactionsData
import build.wallet.bitcoin.transactions.TransactionsData.TransactionsLoadedData
import build.wallet.bitcoin.transactions.TransactionsService
import build.wallet.bitkey.relationships.Invitation
import build.wallet.coachmark.CoachmarkIdentifier
import build.wallet.coachmark.CoachmarkService
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.feature.flags.MobilePayRevampFeatureFlag
import build.wallet.feature.flags.SellBitcoinFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.fwup.FirmwareData
import build.wallet.fwup.FirmwareDataService
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTaskDao
import build.wallet.inappsecurity.MoneyHomeHiddenStatus
import build.wallet.inappsecurity.MoneyHomeHiddenStatusProvider
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.ExchangeRateService
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.partnerships.PartnerRedirectionMethod
import build.wallet.partnerships.PartnershipTransaction
import build.wallet.platform.haptics.Haptics
import build.wallet.platform.haptics.HapticsEffect
import build.wallet.platform.links.DeepLinkHandler
import build.wallet.platform.links.OpenDeeplinkResult
import build.wallet.platform.links.OpenDeeplinkResult.AppRestrictionResult.*
import build.wallet.recovery.sweep.SweepService
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.list.ListModel
import build.wallet.statemachine.limit.MobilePayOnboardingScreenModel
import build.wallet.statemachine.limit.SpendingLimitsCopy
import build.wallet.statemachine.money.amount.MoneyAmountModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.moneyhome.MoneyHomeButtonsModel
import build.wallet.statemachine.moneyhome.card.MoneyHomeCardsModel
import build.wallet.statemachine.moneyhome.card.MoneyHomeCardsProps
import build.wallet.statemachine.moneyhome.card.MoneyHomeCardsUiStateMachine
import build.wallet.statemachine.moneyhome.card.backup.CloudBackupHealthCardUiProps
import build.wallet.statemachine.moneyhome.card.bitcoinprice.BitcoinPriceCardUiProps
import build.wallet.statemachine.moneyhome.card.fwup.DeviceUpdateCardUiProps
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedCardUiProps
import build.wallet.statemachine.moneyhome.card.replacehardware.SetupHardwareCardUiProps
import build.wallet.statemachine.moneyhome.card.sweep.StartSweepCardUiProps
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.*
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingBalanceUiState.BottomSheetDisplayState.*
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingTransactionUiState.EntryPoint.BALANCE
import build.wallet.statemachine.partnerships.AddBitcoinBottomSheetDisplayState
import build.wallet.statemachine.partnerships.AddBitcoinUiProps
import build.wallet.statemachine.partnerships.AddBitcoinUiStateMachine
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryStatusCardUiProps
import build.wallet.statemachine.recovery.losthardware.initiate.InstructionsStyle
import build.wallet.statemachine.recovery.socrec.RecoveryContactCardsUiProps
import build.wallet.statemachine.recovery.socrec.view.ViewingInvitationProps
import build.wallet.statemachine.recovery.socrec.view.ViewingInvitationUiStateMachine
import build.wallet.statemachine.recovery.socrec.view.ViewingRecoveryContactProps
import build.wallet.statemachine.recovery.socrec.view.ViewingRecoveryContactUiStateMachine
import build.wallet.statemachine.settings.full.device.fingerprints.AddAdditionalFingerprintGettingStartedModel
import build.wallet.statemachine.settings.full.device.fingerprints.PromptingForFingerprintFwUpSheetModel
import build.wallet.statemachine.status.AppFunctionalityStatusAlertModel
import build.wallet.statemachine.transactions.TransactionListUiProps
import build.wallet.statemachine.transactions.TransactionListUiStateMachine
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.coachmark.CoachmarkModel
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MoneyHomeViewingBalanceUiStateMachineImpl(
  private val addBitcoinUiStateMachine: AddBitcoinUiStateMachine,
  private val appFunctionalityService: AppFunctionalityService,
  private val deepLinkHandler: DeepLinkHandler,
  private val eventTracker: EventTracker,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val gettingStartedTaskDao: GettingStartedTaskDao,
  private val moneyHomeCardsUiStateMachine: MoneyHomeCardsUiStateMachine,
  private val transactionListUiStateMachine: TransactionListUiStateMachine,
  private val viewingInvitationUiStateMachine: ViewingInvitationUiStateMachine,
  private val viewingRecoveryContactUiStateMachine: ViewingRecoveryContactUiStateMachine,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val moneyHomeHiddenStatusProvider: MoneyHomeHiddenStatusProvider,
  private val coachmarkService: CoachmarkService,
  private val sweepService: SweepService,
  private val haptics: Haptics,
  private val firmwareDataService: FirmwareDataService,
  private val transactionsService: TransactionsService,
  private val sellBitcoinFeatureFlag: SellBitcoinFeatureFlag,
  private val mobilePayRevampFeatureFlag: MobilePayRevampFeatureFlag,
  private val exchangeRateService: ExchangeRateService,
) : MoneyHomeViewingBalanceUiStateMachine {
  @Composable
  override fun model(props: MoneyHomeViewingBalanceUiProps): ScreenModel {
    val scope = rememberStableCoroutineScope()
    if (props.state.isRefreshing) {
      LaunchedEffect("refresh-transactions") {
        transactionsService.syncTransactions()
        sweepService.checkForSweeps()
        exchangeRateService.requestSync()
        props.setState(props.state.copy(isRefreshing = false))
      }
    }
    val transactionsData = remember { transactionsService.transactionsData() }
      .collectAsState()
      .value
    val transactions = when (transactionsData) {
      LoadingTransactionsData -> immutableListOf()
      is TransactionsLoadedData -> transactionsData.transactions
    }

    val numberOfVisibleTransactions = 5

    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()

    val appFunctionalityStatus = remember { appFunctionalityService.status }.collectAsState().value

    val hideBalance by remember {
      moneyHomeHiddenStatusProvider.hiddenStatus.mapLatest {
          status ->
        status == MoneyHomeHiddenStatus.HIDDEN
      }.stateIn(scope = scope, SharingStarted.Eagerly, moneyHomeHiddenStatusProvider.hiddenStatus.value == MoneyHomeHiddenStatus.HIDDEN)
    }.collectAsState()

    var coachmarksToDisplay by remember { mutableStateOf(listOf<CoachmarkIdentifier>()) }
    var coachmarkDisplayed by remember { mutableStateOf(false) }
    LaunchedEffect("coachmarks", coachmarkDisplayed) {
      coachmarkService
        .coachmarksToDisplay(
          setOf(CoachmarkIdentifier.MultipleFingerprintsCoachmark, CoachmarkIdentifier.BiometricUnlockCoachmark, CoachmarkIdentifier.HiddenBalanceCoachmark)
        ).onSuccess {
          coachmarksToDisplay = it
        }
    }

    var alertModel: ButtonAlertModel? by remember { mutableStateOf(null) }

    val viewingBalanceModel =
      ScreenModel(
        alertModel = alertModel,
        body =
          MoneyHomeBodyModel(
            hideBalance = hideBalance,
            onHideBalance = {
              scope.launch {
                moneyHomeHiddenStatusProvider.toggleStatus()
                haptics.vibrate(HapticsEffect.MediumClick)
                coachmarkService.markCoachmarkAsDisplayed(
                  coachmarkId = CoachmarkIdentifier.HiddenBalanceCoachmark
                )
                coachmarkDisplayed = true
              }
            },
            onSettings = props.onSettings,
            balanceModel =
              // if fiat balance is null because currency conversion hasn't happened yet, we will show
              // the sats value as the primary until the fiat balance isn't null
              MoneyAmountModel(
                primaryAmount =
                  when (transactionsData) {
                    LoadingTransactionsData -> ""
                    is TransactionsLoadedData -> when (val balance = transactionsData.fiatBalance) {
                      null -> moneyDisplayFormatter.format(transactionsData.balance.total)
                      else -> moneyDisplayFormatter.format(balance)
                    }
                  },
                secondaryAmount = when (transactionsData) {
                  LoadingTransactionsData -> ""
                  is TransactionsLoadedData -> when (transactionsData.fiatBalance) {
                    null -> ""
                    else ->
                      moneyDisplayFormatter.format(transactionsData.balance.total)
                  }
                }
              ),
            cardsModel =
              MoneyHomeCardsModel(
                props = props,
                onShowAlert = { alertModel = it },
                onDismissAlert = { alertModel = null }
              ),
            buttonsModel =
              MoneyHomeButtonsModel(
                props = props,
                appFunctionalityStatus = appFunctionalityStatus,
                sellBitcoinEnabled = sellBitcoinFeatureFlag.isEnabled(),
                onShowAlert = { alertModel = it },
                onDismissAlert = { alertModel = null }
              ),
            transactionsModel = MoneyHomeTransactionsModel(
              props,
              transactions = transactions,
              fiatCurrency,
              numberOfVisibleTransactions
            ),
            seeAllButtonModel =
              if (transactions.size <= numberOfVisibleTransactions) {
                null
              } else {
                ButtonModel(
                  text = "See All",
                  treatment = ButtonModel.Treatment.Secondary,
                  size = ButtonModel.Size.Footer,
                  onClick =
                    StandardClick {
                      props.setState(ViewingAllTransactionActivityUiState)
                    }
                )
              },
            coachmark = if (coachmarksToDisplay.contains(CoachmarkIdentifier.HiddenBalanceCoachmark)) {
              CoachmarkModel(
                identifier = CoachmarkIdentifier.HiddenBalanceCoachmark,
                title = "Tap to hide balance",
                description = "Now you can easily conceal your balance by tapping to hide.",
                arrowPosition = CoachmarkModel.ArrowPosition(
                  vertical = CoachmarkModel.ArrowPosition.Vertical.Top,
                  horizontal = CoachmarkModel.ArrowPosition.Horizontal.Centered
                ),
                button = null,
                image = null,
                dismiss = {
                  scope.launch {
                    coachmarkService.markCoachmarkAsDisplayed(coachmarkId = CoachmarkIdentifier.HiddenBalanceCoachmark)
                    coachmarkDisplayed = true
                  }
                }
              )
            } else {
              null
            },
            refresh = { transactionsService.syncTransactions() },
            onRefresh = {
              props.setState(props.state.copy(isRefreshing = true))
            },
            isRefreshing = props.state.isRefreshing,
            badgedSettingsIcon =
              coachmarksToDisplay.contains(CoachmarkIdentifier.BiometricUnlockCoachmark) ||
                coachmarksToDisplay.contains(
                  CoachmarkIdentifier.MultipleFingerprintsCoachmark
                ),
            onOpenPriceDetails = {
              props.setState(ShowingPriceChartUiState())
            }
          ),
        bottomSheetModel =
          MoneyHomeBottomSheetModel(
            props = props,
            onShowAlert = { alertModel = it },
            onDismissAlert = { alertModel = null }
          ),
        statusBannerModel = props.homeStatusBannerModel
      )

    return when (val contact = props.state.selectedContact) {
      null -> viewingBalanceModel
      is Invitation ->
        viewingInvitationUiStateMachine.model(
          ViewingInvitationProps(
            hostScreen = viewingBalanceModel,
            fullAccount = props.accountData.account,
            invitation = contact,
            onExit = {
              props.setState(props.state.copy(selectedContact = null))
            }
          )
        )
      else -> viewingRecoveryContactUiStateMachine.model(
        ViewingRecoveryContactProps(
          screenBody = viewingBalanceModel.body,
          recoveryContact = contact,
          account = props.accountData.account,
          afterContactRemoved = {
            props.setState(props.state.copy(selectedContact = null))
          },
          onExit = {
            props.setState(props.state.copy(selectedContact = null))
          }
        )
      )
    }
  }

  @Composable
  private fun MoneyHomeCardsModel(
    props: MoneyHomeViewingBalanceUiProps,
    onShowAlert: (ButtonAlertModel) -> Unit,
    onDismissAlert: () -> Unit,
  ): MoneyHomeCardsModel {
    return moneyHomeCardsUiStateMachine.model(
      props =
        MoneyHomeCardsProps(
          cloudBackupHealthCardUiProps = CloudBackupHealthCardUiProps(
            onActionClick = { status ->
              props.setState(FixingCloudBackupState(status))
            }
          ),
          deviceUpdateCardUiProps =
            DeviceUpdateCardUiProps(
              onUpdateDevice = { firmwareData ->
                props.setState(FwupFlowUiState(firmwareData = firmwareData))
              }
            ),
          gettingStartedCardUiProps =
            GettingStartedCardUiProps(
              accountData = props.accountData,
              onAddBitcoin = {
                props.setState(
                  ViewingBalanceUiState(
                    bottomSheetDisplayState = Partners(
                      initialState = AddBitcoinBottomSheetDisplayState.ShowingPurchaseOrTransferUiState
                    )
                  )
                )
              },
              onEnableSpendingLimit = {
                props.setState(
                  ViewingBalanceUiState(bottomSheetDisplayState = MobilePay(skipped = false))
                )
              },
              onInviteTrustedContact = {
                props.setState(
                  ViewingBalanceUiState(bottomSheetDisplayState = TrustedContact(skipped = false))
                )
              },
              onAddAdditionalFingerprint = {
                props.setState(
                  ViewingBalanceUiState(
                    bottomSheetDisplayState = AddingAdditionalFingerprint(
                      skipped = false
                    )
                  )
                )
              },
              onShowAlert = onShowAlert,
              onDismissAlert = onDismissAlert
            ),
          hardwareRecoveryStatusCardUiProps =
            HardwareRecoveryStatusCardUiProps(
              lostHardwareRecoveryData = props.accountData.lostHardwareRecoveryData,
              onClick = {
                props.setState(
                  ViewHardwareRecoveryStatusUiState(InstructionsStyle.Independent)
                )
              }
            ),
          recoveryContactCardsUiProps =
            RecoveryContactCardsUiProps(
              onClick = { props.setState(props.state.copy(selectedContact = it)) }
            ),
          setupHardwareCardUiProps =
            SetupHardwareCardUiProps(
              onReplaceDevice = {
                props.setState(
                  ViewHardwareRecoveryStatusUiState(InstructionsStyle.ResumedRecoveryAttempt)
                )
              }
            ),
          startSweepCardUiProps = StartSweepCardUiProps(
            onStartSweepClicked = props.onStartSweepFlow
          ),
          bitcoinPriceCardUiProps = BitcoinPriceCardUiProps(
            fullAccountId = props.accountData.account.accountId,
            f8eEnvironment = props.accountData.account.config.f8eEnvironment,
            onOpenPriceChart = { props.setState(ShowingPriceChartUiState()) }
          )
        )
    )
  }

  @Composable
  private fun MoneyHomeButtonsModel(
    props: MoneyHomeViewingBalanceUiProps,
    appFunctionalityStatus: AppFunctionalityStatus,
    sellBitcoinEnabled: Boolean,
    onShowAlert: (ButtonAlertModel) -> Unit,
    onDismissAlert: () -> Unit,
  ): MoneyHomeButtonsModel {
    fun showAlertForLimitedStatus() {
      when (appFunctionalityStatus) {
        is AppFunctionalityStatus.FullFunctionality -> Unit // Nothing to do
        is AppFunctionalityStatus.LimitedFunctionality ->
          onShowAlert(
            AppFunctionalityStatusAlertModel(
              status = appFunctionalityStatus,
              onDismiss = onDismissAlert
            )
          )
      }
    }

    return MoneyHomeButtonsModel.MoneyMovementButtonsModel(
      addButton = MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button(
        enabled = appFunctionalityStatus.featureStates.deposit == Available,
        onClick = {
          if (appFunctionalityStatus.featureStates.deposit == Available) {
            val initialState = if (sellBitcoinEnabled) {
              AddBitcoinBottomSheetDisplayState.PurchasingUiState(selectedAmount = null)
            } else {
              AddBitcoinBottomSheetDisplayState.ShowingPurchaseOrTransferUiState
            }
            props.setState(ViewingBalanceUiState(bottomSheetDisplayState = Partners(initialState)))
          } else {
            showAlertForLimitedStatus()
          }
        }
      ),
      sellButton =
        if (sellBitcoinEnabled) {
          MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button(
            enabled = appFunctionalityStatus.featureStates.sell == Available,
            onClick = {
              if (appFunctionalityStatus.featureStates.sell == Available) {
                props.setState(SellFlowUiState)
              } else {
                showAlertForLimitedStatus()
              }
            }
          )
        } else {
          null
        },
      sendButton =
        MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button(
          enabled = appFunctionalityStatus.featureStates.send == Available,
          onClick = {
            if (appFunctionalityStatus.featureStates.send == Available) {
              props.setState(SendFlowUiState)
            } else {
              showAlertForLimitedStatus()
            }
          }
        ),
      receiveButton = MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button(
        enabled = appFunctionalityStatus.featureStates.receive == Available,
        onClick = {
          if (appFunctionalityStatus.featureStates.receive == Available) {
            if (sellBitcoinEnabled) {
              val initialState = AddBitcoinBottomSheetDisplayState.TransferringUiState
              props.setState(
                ViewingBalanceUiState(
                  bottomSheetDisplayState = Partners(initialState)
                )
              )
            } else {
              props.setState(ReceiveFlowUiState)
            }
          } else {
            showAlertForLimitedStatus()
          }
        }
      )
    )
  }

  @Composable
  private fun MoneyHomeTransactionsModel(
    props: MoneyHomeViewingBalanceUiProps,
    transactions: ImmutableList<BitcoinTransaction>,
    fiatCurrency: FiatCurrency,
    numberOfVisibleTransactions: Int,
  ): ListModel? {
    return transactionListUiStateMachine.model(
      props = TransactionListUiProps(
        transactionVisibility = TransactionListUiProps.TransactionVisibility.Some(
          numberOfVisibleTransactions
        ),
        transactions = transactions,
        fiatCurrency = fiatCurrency,
        onTransactionClicked = { transaction ->
          props.setState(
            ViewingTransactionUiState(
              transaction = transaction,
              entryPoint = BALANCE
            )
          )
        }
      )
    )?.let { transactionGroups ->
      ListModel(
        headerText = "Recent activity",
        sections = transactionGroups
      )
    }
  }

  @Composable
  private fun MoneyHomeBottomSheetModel(
    props: MoneyHomeViewingBalanceUiProps,
    onShowAlert: (ButtonAlertModel) -> Unit,
    onDismissAlert: () -> Unit,
  ): SheetModel? {
    return when (val globalBottomSheet = props.homeBottomSheetModel) {
      null -> {
        when (val currentState = props.state.bottomSheetDisplayState) {
          is Partners ->
            addBitcoinUiStateMachine.model(
              props =
                AddBitcoinUiProps(
                  account = props.accountData.account,
                  initialState = currentState.initialState,
                  keybox = props.accountData.account.keybox,
                  sellBitcoinEnabled = sellBitcoinFeatureFlag.isEnabled(),
                  onAnotherWalletOrExchange = { props.setState(ReceiveFlowUiState) },
                  onPartnerRedirected = { redirectMethod, transaction ->
                    handlePartnerRedirected(
                      method = redirectMethod,
                      transaction = transaction,
                      props = props,
                      onShowAlert = onShowAlert,
                      onDismissAlert = onDismissAlert
                    )
                  },
                  onExit = {
                    props.setState(props.state.copy(bottomSheetDisplayState = null))
                  },
                  onSelectCustomAmount = { minAmount, maxAmount ->
                    props.setState(SelectCustomPartnerPurchaseAmountState(minAmount, maxAmount))
                  }
                )
            )
          is MobilePay -> {
            if (currentState.skipped) {
              LaunchedEffect("skipping-saving-spending-limit") {
                gettingStartedTaskDao.updateTask(
                  id = GettingStartedTask.TaskId.EnableSpendingLimit,
                  state = GettingStartedTask.TaskState.Complete
                ).onSuccess { eventTracker.track(Action.ACTION_APP_MOBILE_TRANSACTION_SKIP) }
                props.setState(props.state.copy(bottomSheetDisplayState = null))
              }
            }
            val onClosed = { props.setState(props.state.copy(bottomSheetDisplayState = null)) }
            val spendingLimitsCopy = SpendingLimitsCopy.get(isRevampOn = mobilePayRevampFeatureFlag.isEnabled())
            MobilePayOnboardingScreenModel(
              onContinue = { props.setState(SetSpendingLimitFlowUiState) },
              onSetUpLater = {
                props.setState(
                  props.state.copy(
                    bottomSheetDisplayState = MobilePay(skipped = true)
                  )
                )
              },
              onClosed = onClosed,
              headerHeadline = spendingLimitsCopy.onboardingModal.headline,
              headerSubline = spendingLimitsCopy.onboardingModal.subline,
              primaryButtonString = spendingLimitsCopy.onboardingModal.primaryButtonString
            ).asSheetModalScreen(onClosed)
          }
          is TrustedContact -> {
            // This has been done here instead of InviteTrustedContactFlowUiStateMachineImpl
            // It's because when done inside there, due to race condition where
            // returning ScreenModel shows up first instead of MoneyHomeViewingBalance screen
            // Maybe due to how long the "gettingStartedTaskDao.updateTask" operation takes
            if (currentState.skipped) {
              LaunchedEffect("skipping-invite-task") {
                gettingStartedTaskDao.updateTask(
                  id = GettingStartedTask.TaskId.InviteTrustedContact,
                  state = GettingStartedTask.TaskState.Complete
                )
                props.setState(props.state.copy(bottomSheetDisplayState = null))
              }
            }
            val onClosed = { props.setState(props.state.copy(bottomSheetDisplayState = null)) }
            ViewingAddTrustedContactFormBodyModel(
              onAddTrustedContact = { props.setState(InviteTrustedContactFlow) },
              onSkip = {
                props.setState(
                  props.state.copy(
                    bottomSheetDisplayState = TrustedContact(
                      skipped = true
                    )
                  )
                )
              },
              onClosed = onClosed
            ).asSheetModalScreen(onClosed)
          }
          is AddingAdditionalFingerprint -> {
            if (currentState.skipped) {
              LaunchedEffect("skipping-add-additional-fingerprint") {
                gettingStartedTaskDao.updateTask(
                  id = GettingStartedTask.TaskId.AddAdditionalFingerprint,
                  state = GettingStartedTask.TaskState.Complete
                ).onSuccess {
                  eventTracker.track(Action.ACTION_APP_ADD_ADDITIONAL_FINGERPRINT_SKIP)
                }
                props.setState(props.state.copy(bottomSheetDisplayState = null))
              }
            }
            val onClosed = { props.setState(props.state.copy(bottomSheetDisplayState = null)) }
            AddAdditionalFingerprintGettingStartedModel(
              onContinue = { props.setState(AddAdditionalFingerprintUiState) },
              onSetUpLater = {
                props.setState(
                  props.state.copy(
                    bottomSheetDisplayState = AddingAdditionalFingerprint(skipped = true)
                  )
                )
              },
              onClosed = onClosed
            ).asSheetModalScreen(onClosed)
          }
          PromptingForFwUpUiState -> {
            val onClosed = { props.setState(props.state.copy(bottomSheetDisplayState = null)) }
            val fwupState = remember { firmwareDataService.firmwareData() }
              .collectAsState()
              .value.firmwareUpdateState

            PromptingForFingerprintFwUpSheetModel(
              onCancel = onClosed,
              onUpdate = {
                when (fwupState) {
                  is FirmwareData.FirmwareUpdateState.PendingUpdate -> props.setState(
                    FwupFlowUiState(fwupState)
                  )
                  FirmwareData.FirmwareUpdateState.UpToDate -> props.setState(
                    props.state.copy(
                      bottomSheetDisplayState = null
                    )
                  )
                }
              }
            )
          }
          null -> null
        }
      }
      else -> globalBottomSheet
    }
  }

  private fun handlePartnerRedirected(
    method: PartnerRedirectionMethod,
    transaction: PartnershipTransaction,
    props: MoneyHomeViewingBalanceUiProps,
    onShowAlert: (ButtonAlertModel) -> Unit,
    onDismissAlert: () -> Unit,
  ) {
    when (method) {
      is PartnerRedirectionMethod.Deeplink -> {
        val result =
          deepLinkHandler.openDeeplink(
            url = method.urlString,
            appRestrictions = method.appRestrictions
          )
        val alertModel: ButtonAlertModel? =
          when (result) {
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
        // Dismiss partners
        props.setState(props.state.copy(bottomSheetDisplayState = null))

        // Show alert if there is one
        alertModel?.let { onShowAlert(alertModel) }
      }

      is PartnerRedirectionMethod.Web -> {
        props.setState(
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
