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
import build.wallet.feature.flags.Bip177FeatureFlag
import build.wallet.feature.flags.DesignSystemUpdatesFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.fwup.FirmwareData
import build.wallet.fwup.FirmwareDataService
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTaskDao
import build.wallet.inappsecurity.MoneyHomeHiddenStatus
import build.wallet.inappsecurity.MoneyHomeHiddenStatusProvider
import build.wallet.money.display.BitcoinDisplayPreferenceRepository
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.platform.haptics.Haptics
import build.wallet.platform.haptics.HapticsEffect
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.list.ListModel
import build.wallet.statemachine.limit.MobilePayOnboardingScreenModel
import build.wallet.statemachine.money.amount.MoneyAmountModel
import build.wallet.statemachine.money.amount.toAnimatedAmountAnimationKey
import build.wallet.statemachine.money.amount.toAnimatedAmountValue
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
import build.wallet.statemachine.moneyhome.full.coachmarks.Bip177CoachmarkModel
import build.wallet.statemachine.moneyhome.full.coachmarks.PrivateWalletHomeCoachmarkModel
import build.wallet.statemachine.moneyhome.full.coachmarks.W3UpgradeCompleteCoachmarkModel
import build.wallet.statemachine.partnerships.AddBitcoinBottomSheetDisplayState
import build.wallet.statemachine.partnerships.AddBitcoinUiProps
import build.wallet.statemachine.partnerships.AddBitcoinUiStateMachine
import build.wallet.statemachine.partnerships.transferlink.PartnerTransferLinkProps
import build.wallet.statemachine.partnerships.transferlink.PartnerTransferLinkUiStateMachine
import build.wallet.statemachine.settings.full.device.fingerprints.PromptingForFingerprintFwUpSheetModel
import build.wallet.statemachine.status.AppFunctionalityStatusAlertModel
import build.wallet.statemachine.transactions.TransactionsActivityModel
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
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.tokens.market.MarketIcons
import build.wallet.wallet.migration.MigrationProgress
import build.wallet.wallet.migration.MigrationService
import build.wallet.wallet.migration.MigrationType
import build.wallet.worker.RefreshExecutor
import build.wallet.worker.runRefreshOperations
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Suppress("LargeClass")
@BitkeyInject(ActivityScope::class)
class MoneyHomeViewingBalanceUiStateMachineImpl(
  private val addBitcoinUiStateMachine: AddBitcoinUiStateMachine,
  private val appFunctionalityService: AppFunctionalityService,
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
  private val partnerTransferLinkUiStateMachine: PartnerTransferLinkUiStateMachine,
  private val migrationService: MigrationService,
  private val bip177FeatureFlag: Bip177FeatureFlag,
  private val designSystemUpdatesFeatureFlag: DesignSystemUpdatesFeatureFlag,
  private val bitcoinDisplayPreferenceRepository: BitcoinDisplayPreferenceRepository,
) : MoneyHomeViewingBalanceUiStateMachine {
  @Composable
  override fun model(props: MoneyHomeViewingBalanceUiProps): ScreenModel {
    val scope = rememberStableCoroutineScope()
    if (props.state.isRefreshing) {
      LaunchedEffect("refresh-transactions") {
        refreshMoneyHomeData()
        props.setState(props.state.copy(isRefreshing = false))
      }
    }
    val transactionsData = remember { bitcoinWalletService.transactionsData() }
      .collectAsState()
      .value

    val appFunctionalityStatus = remember { appFunctionalityService.status }.collectAsState().value

    val hideBalance by remember {
      moneyHomeHiddenStatusProvider.hiddenStatus.mapAsStateFlow(scope) { status ->
        status == MoneyHomeHiddenStatus.HIDDEN
      }
    }.collectAsState()

    // Check if migration is available by calling resume()
    var isMigrationAvailable by remember { mutableStateOf(false) }
    LaunchedEffect("check-migration-status") {
      migrationService.resume(MigrationType.PrivateWalletMigration)
        .onSuccess { progress ->
          isMigrationAvailable = progress is MigrationProgress.NotStarted
        }
    }

    val isBip177Enabled by remember {
      bip177FeatureFlag.flagValue().map { it.isEnabled() }
    }.collectAsState(initial = bip177FeatureFlag.isEnabled())
    val isDesignSystemV2Enabled by remember {
      designSystemUpdatesFeatureFlag.flagValue().map { it.isEnabled() }
    }.collectAsState(initial = designSystemUpdatesFeatureFlag.isEnabled())
    val bitcoinDisplayUnit by bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.collectAsState()

    var coachmarksToDisplay by remember { mutableStateOf(listOf<CoachmarkIdentifier>()) }
    var coachmarkDisplayed by remember { mutableStateOf(0) }

    LaunchedEffect(
      "coachmarks",
      coachmarkDisplayed,
      isBip177Enabled,
      bitcoinDisplayUnit,
      props.state.showW3UpgradeCompleteCoachmark,
      transactionsData != null
    ) {
      val requestedCoachmarks = mutableSetOf(
        CoachmarkIdentifier.Bip177Coachmark,
        CoachmarkIdentifier.PrivateWalletHomeCoachmark
      ).apply {
        if (props.state.showW3UpgradeCompleteCoachmark && transactionsData != null) {
          add(CoachmarkIdentifier.W3UpgradeCompleteCoachmark)
        }
      }

      coachmarkService
        .coachmarksToDisplay(requestedCoachmarks)
        .onSuccess {
          coachmarksToDisplay = it
        }
    }

    var alertModel: ButtonAlertModel? by remember { mutableStateOf(null) }

    val transactionProps = TransactionsActivityProps(
      transactionVisibility = Some(),
      onTransactionClicked = { transaction ->
        props.setState(
          ViewingTransactionUiState(
            transaction = transaction,
            entryPoint = BALANCE
          )
        )
      }
    )
    val transactionsModel = transactionsActivityUiStateMachine.model(
      props = transactionProps
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
          balanceModel = createBalanceModel(transactionsData),
          cardsModel = MoneyHomeCardsModel(
            props = props,
            onShowAlert = { alertModel = it },
            onDismissAlert = { alertModel = null }
          ),
          buttonsModel = MoneyHomeButtonsModel(
            props = props,
            appFunctionalityStatus = appFunctionalityStatus,
            onShowAlert = { alertModel = it },
            onDismissAlert = { alertModel = null }
          ),
          transactionsModel = createTransactionsListModel(
            transactionsModel = transactionsModel,
            isLoading = transactionsData == null,
            isDesignSystemV2Enabled = props.isDesignSystemV2Enabled
          ),
          seeAllButtonModel = createSeeAllButtonModel(transactionsModel, props),
          coachmark = transactionsData?.let {
            createCoachmarkModel(
              coachmarksToDisplay = coachmarksToDisplay.toImmutableList(),
              isMigrationAvailable = isMigrationAvailable,
              scope = scope,
              props = props,
              onCoachmarkDisplayed = { coachmarkDisplayed++ }
            )
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
              iconModel = if (isDesignSystemV2Enabled) {
                IconModel(
                  icon = MarketIcons.EllipsisHorizontal,
                  iconSize = IconSize.HeaderToolbar,
                  iconBackgroundType = IconBackgroundType.Circle(
                    circleSize = IconSize.Regular,
                    color = IconBackgroundType.Circle.CircleColor.SubtleBackground
                  ),
                  iconTint = IconTint.Foreground
                )
              } else {
                IconModel(
                  icon = Icon.SmallIconSettings,
                  iconSize = IconSize.HeaderToolbar,
                  iconBackgroundType = IconBackgroundType.Transient
                )
              },
              testTag = "money-home-settings",
              onClick = StandardClick({ props.onSettings() })
            )
          ),
          onSecurityHubTabClick = {
            props.onGoToSecurityHub()
            scope.launch {
              haptics.vibrate(effect = HapticsEffect.LightClick)
            }
          },
          isSecurityHubBadged = securityActionsService.hasRecommendationsRequiringAttention()
            .collectAsState(false).value,
          haptics = haptics
        ),
        presentationStyle = if (isDesignSystemV2Enabled) {
          ScreenPresentationStyle.RootFullScreen
        } else {
          ScreenPresentationStyle.Root
        },
        bottomSheetModel = MoneyHomeBottomSheetModel(
          props = props
        ),
        statusBannerModel = props.homeStatusBannerModel
      )

    return selectFinalScreenModel(
      viewingBalanceModel = viewingBalanceModel,
      props = props
    )
  }

  private suspend fun refreshMoneyHomeData() {
    refreshExecutor.runRefreshOperations(TransactionActivityOperations)
    transactionsActivityService.sync()
  }

  private fun createBalanceModel(
    transactionsData: build.wallet.bitcoin.transactions.TransactionsData?,
  ): MoneyAmountModel {
    val primaryBalance =
      when (transactionsData) {
        null -> null
        else -> transactionsData.fiatBalance ?: transactionsData.balance.total
      }

    // if fiat balance is null because currency conversion hasn't happened yet, we will show
    // the sats value as the primary until the fiat balance isn't null
    return MoneyAmountModel(
      primaryAmount = primaryBalance?.let(moneyDisplayFormatter::format).orEmpty(),
      primaryAmountValue = primaryBalance?.toAnimatedAmountValue(),
      primaryAmountAnimationKey = primaryBalance?.toAnimatedAmountAnimationKey() ?: 0L,
      secondaryAmount = when (transactionsData) {
        null -> ""
        else -> when (transactionsData.fiatBalance) {
          null -> ""
          else ->
            moneyDisplayFormatter.format(transactionsData.balance.total)
        }
      },
      isLoading = transactionsData == null
    )
  }

  private fun createTransactionsListModel(
    transactionsModel: TransactionsActivityModel?,
    isLoading: Boolean,
    isDesignSystemV2Enabled: Boolean,
  ): ListModel? {
    if (transactionsModel == null && !isLoading && isDesignSystemV2Enabled) {
      return ListModel(
        headerText = "Recent activity",
        sections = immutableListOf()
      )
    }
    return transactionsModel?.let {
      ListModel(
        headerText = "Recent activity",
        sections = immutableListOf(it.listModel)
      )
    }
  }

  private fun createSeeAllButtonModel(
    transactionsModel: TransactionsActivityModel?,
    props: MoneyHomeViewingBalanceUiProps,
  ): ButtonModel? {
    val showSeeAllButton = transactionsModel?.hasMoreTransactions ?: false
    return if (showSeeAllButton) {
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
  }

  @Composable
  private fun createCoachmarkModel(
    coachmarksToDisplay: kotlinx.collections.immutable.ImmutableList<CoachmarkIdentifier>,
    isMigrationAvailable: Boolean,
    scope: kotlinx.coroutines.CoroutineScope,
    props: MoneyHomeViewingBalanceUiProps,
    onCoachmarkDisplayed: () -> Unit,
  ): CoachmarkModel? {
    return when {
      props.state.showW3UpgradeCompleteCoachmark &&
        coachmarksToDisplay.contains(CoachmarkIdentifier.W3UpgradeCompleteCoachmark) -> {
        W3UpgradeCompleteCoachmarkModel(
          onDismiss = {
            props.setState(
              props.state.copy(showW3UpgradeCompleteCoachmark = false)
            )
            scope.launch {
              coachmarkService.markCoachmarkAsDisplayed(
                coachmarkId = CoachmarkIdentifier.W3UpgradeCompleteCoachmark
              )
              onCoachmarkDisplayed()
            }
          }
        )
      }
      coachmarksToDisplay.contains(CoachmarkIdentifier.Bip177Coachmark) -> {
        Bip177CoachmarkModel(
          onDismiss = {
            scope.launch {
              coachmarkService.markCoachmarkAsDisplayed(
                coachmarkId = CoachmarkIdentifier.Bip177Coachmark
              )
              onCoachmarkDisplayed()
            }
          }
        )
      }

      isMigrationAvailable &&
        coachmarksToDisplay.contains(CoachmarkIdentifier.PrivateWalletHomeCoachmark) -> {
        val markCoachmarkAsDisplayed: () -> Unit = {
          scope.launch {
            coachmarkService.markCoachmarkAsDisplayed(
              coachmarkId = CoachmarkIdentifier.PrivateWalletHomeCoachmark
            )
            onCoachmarkDisplayed()
          }
        }
        PrivateWalletHomeCoachmarkModel(
          onDismiss = markCoachmarkAsDisplayed,
          onGoToPrivateWalletMigration = {
            markCoachmarkAsDisplayed()
            props.onGoToPrivateWalletMigration()
          }
        )
      }
      else -> null
    }
  }

  @Composable
  private fun selectFinalScreenModel(
    viewingBalanceModel: ScreenModel,
    props: MoneyHomeViewingBalanceUiProps,
  ): ScreenModel {
    return if (props.state.partnerTransferLinkRequest != null) {
      partnerTransferLinkUiStateMachine.model(
        PartnerTransferLinkProps(
          hostScreen = viewingBalanceModel,
          request = props.state.partnerTransferLinkRequest,
          onComplete = {
            // Clear the request and return to normal viewing balance
            props.setState(props.state.copy(partnerTransferLinkRequest = null))
          },
          onExit = {
            // Clear the request and return to normal viewing balance
            props.setState(props.state.copy(partnerTransferLinkRequest = null))
          }
        )
      )
    } else {
      selectContactScreenModel(viewingBalanceModel, props)
    }
  }

  @Composable
  private fun selectContactScreenModel(
    viewingBalanceModel: ScreenModel,
    props: MoneyHomeViewingBalanceUiProps,
  ): ScreenModel {
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
    val firmwareUpdateState = remember { firmwareDataService.firmwareData() }
      .collectAsState()
      .value
      .firmwareUpdateState

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
              onUpdateFirmware = {
                (firmwareUpdateState as? FirmwareData.FirmwareUpdateState.PendingUpdate)?.let {
                    pendingUpdate ->
                  props.setState(FwupFlowUiState(pendingUpdate))
                }
              },
              showUpdateFirmwareTile =
                firmwareUpdateState is FirmwareData.FirmwareUpdateState.PendingUpdate,
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
            props.setState(ReceiveFlowUiState)
          } else {
            showAlertForLimitedStatus()
          }
        }
      )
    )
  }

  @Composable
  private fun MoneyHomeBottomSheetModel(props: MoneyHomeViewingBalanceUiProps): SheetModel? {
    return when (val currentState = props.state.bottomSheetDisplayState) {
      is Partners ->
        addBitcoinUiStateMachine.model(
          props =
            AddBitcoinUiProps(
              account = props.account as FullAccount,
              initialState = currentState.initialState,
              keybox = props.account.keybox,
              onTransfer = { props.setState(ReceiveFlowUiState) },
              onExit = {
                props.setState(props.state.copy(bottomSheetDisplayState = null))
              },
              onSelectCustomAmount = { minAmount, maxAmount ->
                props.setState(SelectCustomPartnerPurchaseAmountState(minAmount, maxAmount))
              },
              onPurchaseAmountConfirmed = { amount ->
                props.onPurchaseAmountConfirmed(amount)
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
}
