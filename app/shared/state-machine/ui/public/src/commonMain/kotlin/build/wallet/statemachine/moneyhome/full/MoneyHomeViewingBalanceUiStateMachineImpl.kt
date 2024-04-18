package build.wallet.statemachine.moneyhome.full

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.AppFunctionalityStatusProvider
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Available
import build.wallet.bitkey.socrec.Invitation
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTaskDao
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.platform.links.DeepLinkHandler
import build.wallet.platform.links.OpenDeeplinkResult
import build.wallet.platform.links.OpenDeeplinkResult.AppRestrictionResult.Failed
import build.wallet.platform.links.OpenDeeplinkResult.AppRestrictionResult.None
import build.wallet.platform.links.OpenDeeplinkResult.AppRestrictionResult.Success
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.list.ListModel
import build.wallet.statemachine.limit.MobilePayOnboardingScreenModel
import build.wallet.statemachine.money.amount.MoneyAmountModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.moneyhome.MoneyHomeButtonsModel
import build.wallet.statemachine.moneyhome.card.MoneyHomeCardsModel
import build.wallet.statemachine.moneyhome.card.MoneyHomeCardsProps
import build.wallet.statemachine.moneyhome.card.MoneyHomeCardsUiStateMachine
import build.wallet.statemachine.moneyhome.card.backup.CloudBackupHealthCardUiProps
import build.wallet.statemachine.moneyhome.card.fwup.DeviceUpdateCardUiProps
import build.wallet.statemachine.moneyhome.card.gettingstarted.GettingStartedCardUiProps
import build.wallet.statemachine.moneyhome.card.replacehardware.ReplaceHardwareCardUiProps
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.FixingCloudBackupState
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.FwupFlowUiState
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ReceiveFlowUiState
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.SelectCustomPartnerPurchaseAmountState
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.SendFlowUiState
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.SetSpendingLimitFlowUiState
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewHardwareRecoveryStatusUiState
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingAllTransactionActivityUiState
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingBalanceUiState
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingBalanceUiState.BottomSheetDisplayState.MobilePay
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingBalanceUiState.BottomSheetDisplayState.Partners
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingBalanceUiState.BottomSheetDisplayState.TrustedContact
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingTransactionUiState
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingTransactionUiState.EntryPoint.BALANCE
import build.wallet.statemachine.partnerships.AddBitcoinUiProps
import build.wallet.statemachine.partnerships.AddBitcoinUiStateMachine
import build.wallet.statemachine.partnerships.PartnerRedirectionMethod
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryStatusCardUiProps
import build.wallet.statemachine.recovery.losthardware.initiate.InstructionsStyle
import build.wallet.statemachine.recovery.socrec.RecoveryContactCardsUiProps
import build.wallet.statemachine.recovery.socrec.view.ViewingInvitationProps
import build.wallet.statemachine.recovery.socrec.view.ViewingInvitationUiStateMachine
import build.wallet.statemachine.recovery.socrec.view.ViewingRecoveryContactProps
import build.wallet.statemachine.recovery.socrec.view.ViewingRecoveryContactUiStateMachine
import build.wallet.statemachine.send.SendEntryPoint
import build.wallet.statemachine.status.AppFunctionalityStatusAlertModel
import build.wallet.statemachine.transactions.TransactionListUiProps
import build.wallet.statemachine.transactions.TransactionListUiStateMachine
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.alert.AlertModel
import build.wallet.ui.model.button.ButtonModel
import com.github.michaelbull.result.onSuccess

class MoneyHomeViewingBalanceUiStateMachineImpl(
  private val addBitcoinUiStateMachine: AddBitcoinUiStateMachine,
  private val appFunctionalityStatusProvider: AppFunctionalityStatusProvider,
  private val deepLinkHandler: DeepLinkHandler,
  private val eventTracker: EventTracker,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val gettingStartedTaskDao: GettingStartedTaskDao,
  private val moneyHomeCardsUiStateMachine: MoneyHomeCardsUiStateMachine,
  private val transactionListUiStateMachine: TransactionListUiStateMachine,
  private val viewingInvitationUiStateMachine: ViewingInvitationUiStateMachine,
  private val viewingRecoveryContactUiStateMachine: ViewingRecoveryContactUiStateMachine,
) : MoneyHomeViewingBalanceUiStateMachine {
  @Composable
  override fun model(props: MoneyHomeViewingBalanceUiProps): ScreenModel {
    if (props.state.isRefreshing) {
      LaunchedEffect("refresh-transactions") {
        props.accountData.transactionsData.syncTransactions()
        props.setState(props.state.copy(isRefreshing = false))
      }
    }
    val numberOfVisibleTransactions = 5

    val appFunctionalityStatus =
      remember {
        appFunctionalityStatusProvider.appFunctionalityStatus(
          props.accountData.account.config.f8eEnvironment
        )
      }.collectAsState(AppFunctionalityStatus.FullFunctionality).value

    var alertModel: AlertModel? by remember { mutableStateOf(null) }

    val viewingBalanceModel =
      ScreenModel(
        alertModel = alertModel,
        body =
          MoneyHomeBodyModel(
            onSettings = props.onSettings,
            balanceModel =
              MoneyAmountModel(
                primaryAmount = moneyDisplayFormatter.format(props.convertedFiatBalance),
                secondaryAmount =
                  moneyDisplayFormatter
                    .format(props.accountData.transactionsData.balance.total)
              ),
            cardsModel =
              MoneyHomeCardsModel(
                props = props,
                appFunctionalityStatus = appFunctionalityStatus,
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
            transactionsModel = MoneyHomeTransactionsModel(props, numberOfVisibleTransactions),
            seeAllButtonModel =
              if (props.accountData.transactionsData.transactions.size <= numberOfVisibleTransactions) {
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
            refresh = props.accountData.transactionsData.syncTransactions,
            onRefresh = { props.setState(props.state.copy(isRefreshing = true)) },
            isRefreshing = props.state.isRefreshing
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
            onRemoveInvitation = props.socRecActions::removeTrustedContact,
            onExit = {
              props.setState(props.state.copy(selectedContact = null))
            },
            onRefreshInvitation = props.socRecActions::refreshInvitation
          )
        )
      else -> viewingRecoveryContactUiStateMachine.model(
        ViewingRecoveryContactProps(
          screenBody = viewingBalanceModel.body,
          recoveryContact = contact,
          account = props.accountData.account,
          onRemoveContact = props.socRecActions::removeTrustedContact,
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
    appFunctionalityStatus: AppFunctionalityStatus,
    onShowAlert: (AlertModel) -> Unit,
    onDismissAlert: () -> Unit,
  ): MoneyHomeCardsModel {
    return moneyHomeCardsUiStateMachine.model(
      props =
        MoneyHomeCardsProps(
          cloudBackupHealthCardUiProps = CloudBackupHealthCardUiProps(
            appFunctionalityStatus = appFunctionalityStatus,
            onActionClick = { status ->
              props.setState(FixingCloudBackupState(status))
            }
          ),
          deviceUpdateCardUiProps =
            DeviceUpdateCardUiProps(
              firmwareData = props.firmwareData,
              onUpdateDevice = { firmwareData ->
                props.setState(FwupFlowUiState(firmwareData = firmwareData))
              }
            ),
          gettingStartedCardUiProps =
            GettingStartedCardUiProps(
              accountData = props.accountData,
              appFunctionalityStatus = appFunctionalityStatus,
              trustedContacts =
                props.socRecRelationships.endorsedTrustedContacts +
                  props.socRecRelationships.invitations,
              onAddBitcoin = {
                props.setState(ViewingBalanceUiState(bottomSheetDisplayState = Partners()))
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
              relationships = props.socRecRelationships,
              onClick = { props.setState(props.state.copy(selectedContact = it)) }
            ),
          replaceHardwareCardUiProps =
            ReplaceHardwareCardUiProps(
              onReplaceDevice = {
                props.setState(
                  ViewHardwareRecoveryStatusUiState(InstructionsStyle.ResumedRecoveryAttempt)
                )
              }
            )
        )
    )
  }

  @Composable
  private fun MoneyHomeButtonsModel(
    props: MoneyHomeViewingBalanceUiProps,
    appFunctionalityStatus: AppFunctionalityStatus,
    onShowAlert: (AlertModel) -> Unit,
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
      sendButton =
        MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button(
          enabled = appFunctionalityStatus.featureStates.send == Available,
          onClick = {
            if (appFunctionalityStatus.featureStates.send == Available) {
              props.setState(SendFlowUiState(entryPoint = SendEntryPoint.SendButton))
            } else {
              showAlertForLimitedStatus()
            }
          }
        ),
      receiveButton =
        MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button(
          enabled = appFunctionalityStatus.featureStates.receive == Available,
          onClick = {
            if (appFunctionalityStatus.featureStates.receive == Available) {
              props.setState(ReceiveFlowUiState)
            } else {
              showAlertForLimitedStatus()
            }
          }
        ),
      addButton =
        MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button(
          enabled = appFunctionalityStatus.featureStates.deposit == Available,
          onClick = {
            if (appFunctionalityStatus.featureStates.deposit == Available) {
              props.setState(ViewingBalanceUiState(bottomSheetDisplayState = Partners()))
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
    numberOfVisibleTransactions: Int,
  ): ListModel? {
    return transactionListUiStateMachine.model(
      props =
        TransactionListUiProps(
          transactionVisibility =
            TransactionListUiProps.TransactionVisibility.Some(
              numberOfVisibleTransactions
            ),
          transactions = props.accountData.transactionsData.transactions,
          fiatCurrency = props.fiatCurrency,
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
    onShowAlert: (AlertModel) -> Unit,
    onDismissAlert: () -> Unit,
  ): SheetModel? {
    return when (val globalBottomSheet = props.homeBottomSheetModel) {
      null -> {
        when (val currentState = props.state.bottomSheetDisplayState) {
          is Partners ->
            addBitcoinUiStateMachine.model(
              props =
                AddBitcoinUiProps(
                  keybox = props.accountData.account.keybox,
                  fiatCurrency = props.fiatCurrency,
                  purchaseAmount = currentState.purchaseAmount,
                  generateAddress = props.accountData.addressData.generateAddress,
                  onAnotherWalletOrExchange = { props.setState(ReceiveFlowUiState) },
                  onPartnerRedirected = {
                    handlePartnerRedirected(method = it, props, onShowAlert, onDismissAlert)
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
              onClosed = onClosed
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
              onAddTrustedContact = { props.setState(MoneyHomeUiState.InviteTrustedContactFlow) },
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
          null -> null
        }
      }
      else -> globalBottomSheet
    }
  }

  private fun handlePartnerRedirected(
    method: PartnerRedirectionMethod,
    props: MoneyHomeViewingBalanceUiProps,
    onShowAlert: (AlertModel) -> Unit,
    onDismissAlert: () -> Unit,
  ) {
    when (method) {
      is PartnerRedirectionMethod.Deeplink -> {
        val result =
          deepLinkHandler.openDeeplink(
            url = method.urlString,
            appRestrictions = method.appRestrictions
          )
        val alertModel: AlertModel? =
          when (result) {
            OpenDeeplinkResult.Failed ->
              AlertModel(
                title = "Failed to open ${method.partnerName}.",
                subline = null,
                onDismiss = onDismissAlert,
                primaryButtonText = "OK",
                onPrimaryButtonClick = onDismissAlert
              )

            is OpenDeeplinkResult.Opened ->
              when (result.appRestrictionResult) {
                is Failed ->
                  AlertModel(
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
          MoneyHomeUiState.ShowingInAppBrowserUiState(
            urlString = method.urlString,
            onClose = {
              props.setState(props.state.copy(bottomSheetDisplayState = null))
            }
          )
        )
      }
    }
  }
}
