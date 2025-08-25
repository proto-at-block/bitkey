package build.wallet.statemachine.moneyhome.full

import androidx.compose.runtime.*
import bitkey.securitycenter.SecurityActionsService
import build.wallet.activity.TransactionActivityOperations
import build.wallet.activity.TransactionsActivityService
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action
import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Available
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.Invitation
import build.wallet.coachmark.CoachmarkIdentifier
import build.wallet.coachmark.CoachmarkService
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.coroutines.scopes.mapAsStateFlow
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.fwup.FirmwareData
import build.wallet.fwup.FirmwareDataService
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTaskDao
import build.wallet.inappsecurity.MoneyHomeHiddenStatus
import build.wallet.inappsecurity.MoneyHomeHiddenStatusProvider
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.partnerships.PartnerRedirectionMethod
import build.wallet.partnerships.PartnershipTransaction
import build.wallet.platform.haptics.Haptics
import build.wallet.platform.haptics.HapticsEffect
import build.wallet.platform.links.DeepLinkHandler
import build.wallet.platform.links.OpenDeeplinkResult
import build.wallet.platform.links.OpenDeeplinkResult.AppRestrictionResult.*
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.list.ListModel
import build.wallet.statemachine.limit.MobilePayOnboardingScreenModel
import build.wallet.statemachine.money.amount.MoneyAmountModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.moneyhome.MoneyHomeButtonsModel
import build.wallet.statemachine.moneyhome.card.CardListModel
import build.wallet.statemachine.moneyhome.card.MoneyHomeCardsProps
import build.wallet.statemachine.moneyhome.card.MoneyHomeCardsUiStateMachine
import build.wallet.statemachine.moneyhome.card.bitcoinprice.BitcoinPriceCardUiProps
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedCardUiProps
import build.wallet.statemachine.moneyhome.card.inheritance.InheritanceCardUiProps
import build.wallet.statemachine.moneyhome.card.sweep.StartSweepCardUiProps
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.*
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingBalanceUiState.BottomSheetDisplayState.*
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingTransactionUiState.EntryPoint.BALANCE
import build.wallet.statemachine.partnerships.AddBitcoinBottomSheetDisplayState
import build.wallet.statemachine.partnerships.AddBitcoinUiProps
import build.wallet.statemachine.partnerships.AddBitcoinUiStateMachine
import build.wallet.statemachine.settings.full.device.fingerprints.AddAdditionalFingerprintGettingStartedModel
import build.wallet.statemachine.settings.full.device.fingerprints.PromptingForFingerprintFwUpSheetModel
import build.wallet.statemachine.status.AppFunctionalityStatusAlertModel
import build.wallet.statemachine.transactions.TransactionsActivityProps
import build.wallet.statemachine.transactions.TransactionsActivityProps.TransactionVisibility.Some
import build.wallet.statemachine.transactions.TransactionsActivityUiStateMachine
import build.wallet.statemachine.trustedcontact.view.ViewingInvitationProps
import build.wallet.statemachine.trustedcontact.view.ViewingInvitationUiStateMachine
import build.wallet.statemachine.trustedcontact.view.ViewingRecoveryContactProps
import build.wallet.statemachine.trustedcontact.view.ViewingRecoveryContactUiStateMachine
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.coachmark.CoachmarkModel
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.worker.RefreshExecutor
import build.wallet.worker.runRefreshOperations
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.launch

@Suppress("LargeClass")
@BitkeyInject(ActivityScope::class)
class MoneyHomeViewingBalanceUiStateMachineImpl(
  private val addBitcoinUiStateMachine: AddBitcoinUiStateMachine,
  private val appFunctionalityService: AppFunctionalityService,
  private val deepLinkHandler: DeepLinkHandler,
  private val eventTracker: EventTracker,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val gettingStartedTaskDao: GettingStartedTaskDao,
  private val moneyHomeCardsUiStateMachine: MoneyHomeCardsUiStateMachine,
  private val transactionsActivityUiStateMachine: TransactionsActivityUiStateMachine,
  private val viewingInvitationUiStateMachine: ViewingInvitationUiStateMachine,
  private val viewingRecoveryContactUiStateMachine: ViewingRecoveryContactUiStateMachine,
  private val moneyHomeHiddenStatusProvider: MoneyHomeHiddenStatusProvider,
  private val coachmarkService: CoachmarkService,
  private val haptics: Haptics,
  private val firmwareDataService: FirmwareDataService,
  private val bitcoinWalletService: BitcoinWalletService,
  private val transactionsActivityService: TransactionsActivityService,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val securityActionsService: SecurityActionsService,
  private val refreshExecutor: RefreshExecutor,
) : MoneyHomeViewingBalanceUiStateMachine {
  @Composable
  override fun model(props: MoneyHomeViewingBalanceUiProps): ScreenModel {
    val scope = rememberStableCoroutineScope()
    if (props.state.isRefreshing) {
      LaunchedEffect("refresh-transactions") {
        refreshExecutor.runRefreshOperations(TransactionActivityOperations)
        transactionsActivityService.sync()
        props.setState(props.state.copy(isRefreshing = false))
      }
    }
    val transactionsData = remember { bitcoinWalletService.transactionsData() }
      .collectAsState()
      .value

    val numberOfVisibleTransactions = 5

    val appFunctionalityStatus = remember { appFunctionalityService.status }.collectAsState().value

    val hideBalance by remember {
      moneyHomeHiddenStatusProvider.hiddenStatus.mapAsStateFlow(scope) { status ->
        status == MoneyHomeHiddenStatus.HIDDEN
      }
    }.collectAsState()

    var coachmarksToDisplay by remember { mutableStateOf(listOf<CoachmarkIdentifier>()) }
    var coachmarkDisplayed by remember { mutableStateOf(false) }
    LaunchedEffect("coachmarks", coachmarkDisplayed) {
      coachmarkService
        .coachmarksToDisplay(
          setOf(
            CoachmarkIdentifier.BalanceGraphCoachmark,
            CoachmarkIdentifier.SecurityHubHomeCoachmark
          )
        ).onSuccess {
          coachmarksToDisplay = it
        }
    }

    var alertModel: ButtonAlertModel? by remember { mutableStateOf(null) }

    val transactionsModel = transactionsActivityUiStateMachine.model(
      props = TransactionsActivityProps(
        transactionVisibility = Some(numberOfVisibleTransactions),
        onTransactionClicked = { transaction ->
          props.setState(
            ViewingTransactionUiState(
              transaction = transaction,
              entryPoint = BALANCE
            )
          )
        }
      )
    )

    val viewingBalanceModel =
      ScreenModel(
        alertModel = alertModel,
        body = MoneyHomeBodyModel(
          hideBalance = hideBalance,
          onHideBalance = {
            scope.launch {
              moneyHomeHiddenStatusProvider.toggleStatus()
              haptics.vibrate(HapticsEffect.MediumClick)
            }
          },
          onSettings = props.onSettings,
          balanceModel =
            // if fiat balance is null because currency conversion hasn't happened yet, we will show
            // the sats value as the primary until the fiat balance isn't null
            MoneyAmountModel(
              primaryAmount =
                when (transactionsData) {
                  null -> ""
                  else -> when (val balance = transactionsData.fiatBalance) {
                    null -> moneyDisplayFormatter.format(transactionsData.balance.total)
                    else -> moneyDisplayFormatter.format(balance)
                  }
                },
              secondaryAmount = when (transactionsData) {
                null -> ""
                else -> when (transactionsData.fiatBalance) {
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
              onShowAlert = { alertModel = it },
              onDismissAlert = { alertModel = null }
            ),
          transactionsModel = transactionsModel?.let {
            ListModel(
              headerText = "Recent activity",
              sections = immutableListOf(it.listModel)
            )
          },
          seeAllButtonModel = run {
            val showSeeAllButton = transactionsModel?.hasMoreTransactions ?: false
            if (showSeeAllButton) {
              ButtonModel(
                text = "See All",
                treatment = ButtonModel.Treatment.Secondary,
                size = ButtonModel.Size.Footer,
                onClick = StandardClick {
                  props.setState(ViewingAllTransactionActivityUiState)
                }
              )
            } else {
              null
            }
          },
          coachmark = when {
            coachmarksToDisplay.contains(CoachmarkIdentifier.BalanceGraphCoachmark) -> {
              coachmarkDisplayed = false
              CoachmarkModel(
                identifier = CoachmarkIdentifier.BalanceGraphCoachmark,
                title = "View your performance",
                description = "Tap the price graph to see the performance of your bitcoin balance over time.",
                arrowPosition = CoachmarkModel.ArrowPosition(
                  vertical = CoachmarkModel.ArrowPosition.Vertical.Top,
                  horizontal = CoachmarkModel.ArrowPosition.Horizontal.Centered
                ),
                button = null,
                image = null,
                dismiss = {
                  scope.launch {
                    coachmarkService.markCoachmarkAsDisplayed(
                      coachmarkId = CoachmarkIdentifier.BalanceGraphCoachmark
                    )
                    coachmarkDisplayed = true
                  }
                }
              )
            }

            coachmarksToDisplay.contains(CoachmarkIdentifier.SecurityHubHomeCoachmark) -> {
              coachmarkDisplayed = false
              CoachmarkModel(
                identifier = CoachmarkIdentifier.SecurityHubHomeCoachmark,
                title = "Bitkey Security, Simplified",
                description = "The new Security Hub gives you a clear view of your setup and lets you know if anything needs your attention.",
                arrowPosition = CoachmarkModel.ArrowPosition(
                  vertical = CoachmarkModel.ArrowPosition.Vertical.Bottom,
                  horizontal = CoachmarkModel.ArrowPosition.Horizontal.Centered
                ),
                button = null,
                image = null,
                dismiss = {
                  scope.launch {
                    coachmarkService.markCoachmarkAsDisplayed(
                      coachmarkId = CoachmarkIdentifier.SecurityHubHomeCoachmark
                    )
                    coachmarkDisplayed = true
                  }
                }
              )
            }
            else -> null
          },
          onRefresh = {
            props.setState(props.state.copy(isRefreshing = true))
          },
          isRefreshing = props.state.isRefreshing,
          onOpenPriceDetails = {
            props.setState(ShowingPriceChartUiState())
          },
          trailingToolbarAccessoryModel = ToolbarAccessoryModel.IconAccessory(
            model = IconButtonModel(
              iconModel = IconModel(
                icon = Icon.SmallIconSettings,
                iconSize = IconSize.HeaderToolbar
              ),
              onClick = StandardClick({ props.onSettings() })
            )
          ),
          onSecurityHubTabClick = {
            props.onGoToSecurityHub()
            scope.launch {
              haptics.vibrate(effect = HapticsEffect.LightClick)
              coachmarkService.markCoachmarkAsDisplayed(CoachmarkIdentifier.SecurityHubHomeCoachmark)
            }
          },
          isSecurityHubBadged = securityActionsService.hasRecommendationsRequiringAttention()
            .collectAsState(false).value
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
            fullAccount = props.account as FullAccount,
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
          account = props.account as FullAccount,
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
  ): CardListModel {
    return moneyHomeCardsUiStateMachine.model(
      props =
        MoneyHomeCardsProps(
          gettingStartedCardUiProps =
            GettingStartedCardUiProps(
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
          startSweepCardUiProps = StartSweepCardUiProps(
            onStartSweepClicked = props.onStartSweepFlow
          ),
          bitcoinPriceCardUiProps = BitcoinPriceCardUiProps(
            accountId = props.account.accountId,
            onOpenPriceChart = { props.setState(ShowingPriceChartUiState()) }
          ),
          inheritanceCardUiProps = inheritanceCardUiProps(props)
        )
    )
  }

  @Composable
  private fun inheritanceCardUiProps(
    props: MoneyHomeViewingBalanceUiProps,
  ): InheritanceCardUiProps {
    return InheritanceCardUiProps(
      isDismissible = true,
      completeClaim = { claim ->
        props.setState(
          CompleteInheritanceClaimUiState(
            relationshipId = claim.relationshipId
          )
        )
      },
      denyClaim = { claim ->
        props.setState(
          DenyInheritanceClaimUiState(
            claimId = claim.claimId
          )
        )
      },
      moveFundsCallToAction = {
        inAppBrowserNavigator.open("https://bitkey.world/hc/retain-control-of-funds") {}
      }
    )
  }

  @Composable
  private fun MoneyHomeButtonsModel(
    props: MoneyHomeViewingBalanceUiProps,
    appFunctionalityStatus: AppFunctionalityStatus,
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
            val initialState =
              AddBitcoinBottomSheetDisplayState.PurchasingUiState(selectedAmount = null)
            props.setState(ViewingBalanceUiState(bottomSheetDisplayState = Partners(initialState)))
          } else {
            showAlertForLimitedStatus()
          }
        }
      ),
      sellButton = MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button(
        enabled = appFunctionalityStatus.featureStates.sell == Available,
        onClick = {
          if (appFunctionalityStatus.featureStates.sell == Available) {
            props.setState(SellFlowUiState)
          } else {
            showAlertForLimitedStatus()
          }
        }
      ),
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
            val initialState = AddBitcoinBottomSheetDisplayState.TransferringUiState
            props.setState(
              ViewingBalanceUiState(
                bottomSheetDisplayState = Partners(initialState)
              )
            )
          } else {
            showAlertForLimitedStatus()
          }
        }
      )
    )
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
                  account = props.account as FullAccount,
                  initialState = currentState.initialState,
                  keybox = props.account.keybox,
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
              headerHeadline = "Transfer without hardware",
              headerSubline = "Spend up to a set daily limit without your Bitkey device.",
              primaryButtonString = "Got it"
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
