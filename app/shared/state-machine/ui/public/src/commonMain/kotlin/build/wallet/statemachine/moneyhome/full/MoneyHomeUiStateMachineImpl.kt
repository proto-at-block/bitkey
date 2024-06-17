package build.wallet.statemachine.moneyhome.full

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.id.MoneyHomeEventTrackerScreenId.MONEY_HOME_ALL_TRANSACTIONS
import build.wallet.bitcoin.invoice.ParsedPaymentData
import build.wallet.bitcoin.invoice.PaymentDataParser
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitkey.socrec.RecoveryContact
import build.wallet.cloud.backup.health.MobileKeyBackupStatus
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.platform.clipboard.Clipboard
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.recovery.socrec.PostSocRecTaskRepository
import build.wallet.statemachine.cloud.health.RepairCloudBackupStateMachine
import build.wallet.statemachine.cloud.health.RepairMobileKeyBackupProps
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.list.ListFormBodyModel
import build.wallet.statemachine.data.firmware.FirmwareData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.LostHardwareRecoveryInProgressData
import build.wallet.statemachine.fwup.FwupNfcUiProps
import build.wallet.statemachine.fwup.FwupNfcUiStateMachine
import build.wallet.statemachine.limit.SetSpendingLimitUiStateMachine
import build.wallet.statemachine.limit.SpendingLimitProps
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.AddAdditionalFingerprintUiState
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.FwupFlowUiState
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.PerformingSweep
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ReceiveFlowUiState
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.SendFlowUiState
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.SetSpendingLimitFlowUiState
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ShowingInAppBrowserUiState
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewHardwareRecoveryStatusUiState
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingAllTransactionActivityUiState
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingBalanceUiState
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingBalanceUiState.BottomSheetDisplayState.Partners
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingBalanceUiState.BottomSheetDisplayState.PromptingForFwUpUiState
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingTransactionUiState
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingTransactionUiState.EntryPoint
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingTransactionUiState.EntryPoint.ACTIVITY
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingTransactionUiState.EntryPoint.BALANCE
import build.wallet.statemachine.partnerships.purchase.CustomAmountEntryUiProps
import build.wallet.statemachine.partnerships.purchase.CustomAmountEntryUiStateMachine
import build.wallet.statemachine.receive.AddressQrCodeUiProps
import build.wallet.statemachine.receive.AddressQrCodeUiStateMachine
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryProps
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryUiStateMachine
import build.wallet.statemachine.recovery.losthardware.initiate.InstructionsStyle
import build.wallet.statemachine.recovery.socrec.inviteflow.InviteTrustedContactFlowUiProps
import build.wallet.statemachine.recovery.socrec.inviteflow.InviteTrustedContactFlowUiStateMachine
import build.wallet.statemachine.recovery.sweep.SweepUiProps
import build.wallet.statemachine.recovery.sweep.SweepUiStateMachine
import build.wallet.statemachine.send.SendEntryPoint
import build.wallet.statemachine.send.SendUiProps
import build.wallet.statemachine.send.SendUiStateMachine
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsProps
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiStateMachine
import build.wallet.statemachine.transactions.TransactionDetailsUiProps
import build.wallet.statemachine.transactions.TransactionDetailsUiStateMachine
import build.wallet.statemachine.transactions.TransactionListUiProps
import build.wallet.statemachine.transactions.TransactionListUiProps.TransactionVisibility.All
import build.wallet.statemachine.transactions.TransactionListUiStateMachine
import com.github.michaelbull.result.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import build.wallet.statemachine.settings.full.device.fingerprints.EntryPoint as FingerprintManagementEntryPoint

class MoneyHomeUiStateMachineImpl(
  private val addressQrCodeUiStateMachine: AddressQrCodeUiStateMachine,
  private val sendUiStateMachine: SendUiStateMachine,
  private val transactionDetailsUiStateMachine: TransactionDetailsUiStateMachine,
  private val transactionListUiStateMachine: TransactionListUiStateMachine,
  private val fwupNfcUiStateMachine: FwupNfcUiStateMachine,
  private val lostHardwareUiStateMachine: LostHardwareRecoveryUiStateMachine,
  private val setSpendingLimitUiStateMachine: SetSpendingLimitUiStateMachine,
  private val inviteTrustedContactFlowUiStateMachine: InviteTrustedContactFlowUiStateMachine,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val clipboard: Clipboard,
  private val paymentDataParser: PaymentDataParser,
  private val recoveryIncompleteRepository: PostSocRecTaskRepository,
  private val moneyHomeViewingBalanceUiStateMachine: MoneyHomeViewingBalanceUiStateMachine,
  private val customAmountEntryUiStateMachine: CustomAmountEntryUiStateMachine,
  private val repairCloudBackupStateMachine: RepairCloudBackupStateMachine,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val managingFingerprintsUiStateMachine: ManagingFingerprintsUiStateMachine,
  private val sweepUiStateMachine: SweepUiStateMachine,
) : MoneyHomeUiStateMachine {
  @Composable
  override fun model(props: MoneyHomeUiProps): ScreenModel {
    var uiState: MoneyHomeUiState by remember(
      props.origin,
      props.accountData.isCompletingSocialRecovery
    ) {
      val initialState = if (props.origin == MoneyHomeUiProps.Origin.Launch) {
        // Navigate directly to hardware recovery when completing hardware recovery
        val lostHardwareRecoveryData = props.accountData.lostHardwareRecoveryData

        when {
          props.accountData.isCompletingSocialRecovery -> {
            ViewHardwareRecoveryStatusUiState(InstructionsStyle.ContinuingRecovery)
          }
          lostHardwareRecoveryData is LostHardwareRecoveryInProgressData ->
            when (lostHardwareRecoveryData.recoveryInProgressData) {
              is CompletingRecoveryData ->
                ViewHardwareRecoveryStatusUiState(
                  InstructionsStyle.Independent
                )
              else -> ViewingBalanceUiState()
            }
          else -> ViewingBalanceUiState()
        }
      } else {
        ViewingBalanceUiState()
      }
      mutableStateOf(initialState)
    }

    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()
    val scope = rememberStableCoroutineScope()

    return when (val state = uiState) {
      is MoneyHomeUiState.FixingCloudBackupState ->
        repairCloudBackupStateMachine.model(
          RepairMobileKeyBackupProps(
            account = props.accountData.account,
            presentationStyle = Modal,
            mobileKeyBackupStatus = state.status,
            onExit = { uiState = ViewingBalanceUiState() },
            onRepaired = { uiState = ViewingBalanceUiState() }
          )
        )

      is ViewingBalanceUiState ->
        moneyHomeViewingBalanceUiStateMachine.model(
          props = MoneyHomeViewingBalanceUiProps(
            accountData = props.accountData,
            firmwareData = props.firmwareData,
            socRecRelationships = props.socRecRelationships,
            socRecActions = props.socRecActions,
            homeBottomSheetModel = props.homeBottomSheetModel,
            homeStatusBannerModel = props.homeStatusBannerModel,
            onSettings = props.onSettings,
            state = state,
            setState = { uiState = it },
            onPartnershipsWebFlowCompleted = props.onPartnershipsWebFlowCompleted,
            onStartSweepFlow = {
              uiState = PerformingSweep
            }
          )
        )

      is PerformingSweep -> sweepUiStateMachine.model(
        SweepUiProps(
          presentationStyle = Modal,
          onExit = { uiState = ViewingBalanceUiState() },
          onSuccess = { uiState = ViewingBalanceUiState() },
          recoveredFactor = null,
          keybox = props.accountData.account.keybox
        )
      )

      ReceiveFlowUiState ->
        ReceiveBitcoinModel(
          props,
          onExit = {
            uiState = ViewingBalanceUiState()
          }
        )

      is SendFlowUiState ->
        SendBitcoinModel(
          props,
          state,
          validPaymentDataInClipboard =
            clipboard.getPlainTextItem()?.let {
              paymentDataParser.decode(
                it.data,
                props.accountData.account.config.bitcoinNetworkType
              ).get()
            },
          onExit = {
            uiState = ViewingBalanceUiState()
          }
        )

      SetSpendingLimitFlowUiState ->
        SetSpendingLimitModel(
          props,
          onExit = {
            uiState = ViewingBalanceUiState()
          }
        )

      is ViewHardwareRecoveryStatusUiState ->
        HardwareRecoveryModel(
          props,
          state.instructionsStyle,
          onExit = {
            uiState = ViewingBalanceUiState()
          },
          scope
        )

      is FwupFlowUiState ->
        FwupFlowModel(
          props = props,
          firmwareData = state.firmwareData,
          onBack = {
            uiState = ViewingBalanceUiState()
          }
        )

      ViewingAllTransactionActivityUiState ->
        AllTransactionsModel(
          props = props,
          fiatCurrency = fiatCurrency,
          onTransactionSelected = { transaction ->
            uiState =
              ViewingTransactionUiState(
                transaction = transaction,
                entryPoint = ACTIVITY
              )
          },
          onExit = {
            uiState = ViewingBalanceUiState()
          }
        )

      is ViewingTransactionUiState ->
        TransactionDetailsModel(
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

      is ShowingInAppBrowserUiState ->
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = state.urlString,
              onClose = state.onClose
            )
          }
        ).asModalScreen()

      is MoneyHomeUiState.InviteTrustedContactFlow ->
        inviteTrustedContactFlowUiStateMachine.model(
          props =
            InviteTrustedContactFlowUiProps(
              account = props.accountData.account,
              onExit = { uiState = ViewingBalanceUiState() }
            )
        )

      is MoneyHomeUiState.SelectCustomPartnerPurchaseAmountState ->
        customAmountEntryUiStateMachine.model(
          props =
            CustomAmountEntryUiProps(
              minimumAmount = state.minimumAmount,
              maximumAmount = state.maximumAmount,
              onBack = {
                uiState =
                  ViewingBalanceUiState(
                    bottomSheetDisplayState =
                      Partners(purchaseAmount = FiatMoney.zero(fiatCurrency))
                  )
              },
              onNext = {
                uiState =
                  ViewingBalanceUiState(
                    bottomSheetDisplayState =
                      Partners(purchaseAmount = it)
                  )
              }
            )
        )
      AddAdditionalFingerprintUiState -> managingFingerprintsUiStateMachine.model(
        ManagingFingerprintsProps(
          account = props.accountData.account,
          onBack = { uiState = ViewingBalanceUiState() },
          onFwUpRequired = {
            uiState = ViewingBalanceUiState(bottomSheetDisplayState = PromptingForFwUpUiState)
          },
          entryPoint = FingerprintManagementEntryPoint.MONEY_HOME
        )
      )
    }
  }

  @Composable
  private fun ReceiveBitcoinModel(
    props: MoneyHomeUiProps,
    onExit: () -> Unit,
  ) = addressQrCodeUiStateMachine.model(
    props =
      AddressQrCodeUiProps(
        accountData = props.accountData,
        onBack = onExit
      )
  ).asModalFullScreen()

  @Composable
  private fun SendBitcoinModel(
    props: MoneyHomeUiProps,
    state: SendFlowUiState,
    validPaymentDataInClipboard: ParsedPaymentData?,
    onExit: () -> Unit,
  ) = sendUiStateMachine.model(
    props = SendUiProps(
      entryPoint = state.entryPoint,
      accountData = props.accountData,
      validInvoiceInClipboard = validPaymentDataInClipboard,
      onExit = onExit,
      // Since hitting "Done" is the same as exiting out of the send flow.
      onDone = onExit
    )
  )

  @Composable
  private fun SetSpendingLimitModel(
    props: MoneyHomeUiProps,
    onExit: () -> Unit,
  ) = setSpendingLimitUiStateMachine.model(
    props = SpendingLimitProps(
      currentSpendingLimit = null,
      accountData = props.accountData,
      onClose = onExit,
      onSetLimit = { onExit() }
    )
  )

  @Composable
  private fun TransactionDetailsModel(
    props: MoneyHomeUiProps,
    state: ViewingTransactionUiState,
    onClose: (EntryPoint) -> Unit,
  ) = transactionDetailsUiStateMachine.model(
    props = TransactionDetailsUiProps(
      accountData = props.accountData,
      transaction = state.transaction,
      onClose = { onClose(state.entryPoint) }
    )
  )

  @Composable
  private fun HardwareRecoveryModel(
    props: MoneyHomeUiProps,
    instructionsStyle: InstructionsStyle,
    onExit: () -> Unit,
    scope: CoroutineScope,
  ) = lostHardwareUiStateMachine.model(
    props =
      LostHardwareRecoveryProps(
        account = props.accountData.account,
        lostHardwareRecoveryData = props.accountData.lostHardwareRecoveryData,
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
        instructionsStyle = instructionsStyle
      )
  )

  @Composable
  fun FwupFlowModel(
    props: MoneyHomeUiProps,
    firmwareData: FirmwareData.FirmwareUpdateState.PendingUpdate,
    onBack: () -> Unit,
  ) = fwupNfcUiStateMachine.model(
    props =
      FwupNfcUiProps(
        firmwareData = firmwareData,
        isHardwareFake = props.accountData.account.config.isHardwareFake,
        onDone = onBack
      )
  )

  @Composable
  private fun AllTransactionsModel(
    props: MoneyHomeUiProps,
    fiatCurrency: FiatCurrency,
    onTransactionSelected: (BitcoinTransaction) -> Unit,
    onExit: () -> Unit,
  ) = ListFormBodyModel(
    toolbarTitle = "Activity",
    listGroups =
      transactionListUiStateMachine.model(
        props = TransactionListUiProps(
          transactionVisibility = All,
          transactions = props.accountData.transactionsData.transactions,
          fiatCurrency = fiatCurrency,
          onTransactionClicked = onTransactionSelected
        )
      ) ?: immutableListOf(),
    onBack = onExit,
    id = MONEY_HOME_ALL_TRANSACTIONS
  ).asRootScreen()
}

sealed interface MoneyHomeUiState {
  /**
   * Indicates that we are viewing the balance, with nothing presented on top
   */
  data class ViewingBalanceUiState(
    val isRefreshing: Boolean = false,
    val bottomSheetDisplayState: BottomSheetDisplayState? = null,
    val urlStringForInAppBrowser: Boolean = false,
    val selectedContact: RecoveryContact? = null,
  ) : MoneyHomeUiState {
    sealed interface BottomSheetDisplayState {
      /**
       * We have entered the partners flow, which is a half-sheet
       * displayed on top of the money home screen
       *
       * @param purchaseAmount - skip ahead to the quotes flow with the given amount
       */
      data class Partners(val purchaseAmount: FiatMoney? = null) : BottomSheetDisplayState

      data class TrustedContact(val skipped: Boolean) : BottomSheetDisplayState

      /**
       * Enabling mobile pay or optionally skipping - shown when user enters from getting
       * started
       *
       * @property skipped - when true, the db operation to mark the getting started task is executed
       */
      data class MobilePay(val skipped: Boolean) : BottomSheetDisplayState

      /**
       * Adding a second fingerprint that can be used for unlocking the hardware, shown
       * from getting started.
       */
      data class AddingAdditionalFingerprint(val skipped: Boolean) : BottomSheetDisplayState

      /**
       * Showing a bottom modal for the user to complete a fwup to access the feature
       * to add an additional fingerprint.
       */
      data object PromptingForFwUpUiState : BottomSheetDisplayState
    }
  }

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
   * Indicates that we are in receive flow.
   */
  data object ReceiveFlowUiState : MoneyHomeUiState

  /**
   * Indicates that we are in the process of fixing cloud backup state.
   */
  data class FixingCloudBackupState(
    val status: MobileKeyBackupStatus.ProblemWithBackup,
  ) : MoneyHomeUiState

  /**
   * Indicates that we are in send flow.
   *
   * @param entryPoint - determines what was the entry point/source of the send flow.
   */
  data class SendFlowUiState(
    val entryPoint: SendEntryPoint,
  ) : MoneyHomeUiState

  /**
   * Indicates that we are viewing details for the given transaction.
   */
  data class ViewingTransactionUiState(
    val transaction: BitcoinTransaction,
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
   * Indicates that we are in the enrolling additional fingerprint flow
   */
  data object AddAdditionalFingerprintUiState : MoneyHomeUiState

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

  data object InviteTrustedContactFlow : MoneyHomeUiState

  /**
   * Indicates that we are in the process of selecting a custom partner purchase amount
   * @param minimumAmount - the minimum amount that can be selected
   * @param maximumAmount - the maximum amount that can be selected
   */
  data class SelectCustomPartnerPurchaseAmountState(
    val minimumAmount: FiatMoney,
    val maximumAmount: FiatMoney,
  ) : MoneyHomeUiState
}
