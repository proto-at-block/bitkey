package build.wallet.statemachine.moneyhome.full

import androidx.compose.runtime.*
import build.wallet.activity.Transaction
import build.wallet.analytics.events.screen.id.MoneyHomeEventTrackerScreenId.MONEY_HOME_ALL_TRANSACTIONS
import build.wallet.bitcoin.invoice.ParsedPaymentData
import build.wallet.bitcoin.invoice.PaymentDataParser
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.inheritance.InheritanceClaimId
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.bitkey.relationships.TrustedContact
import build.wallet.cloud.backup.health.MobileKeyBackupStatus
import build.wallet.compose.collections.buildImmutableList
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.InheritanceMarketingFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.fwup.FirmwareData
import build.wallet.inheritance.InheritanceUpsellService
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.onboarding.OnboardingCompletionService
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnershipEvent
import build.wallet.partnerships.PartnershipTransactionId
import build.wallet.partnerships.PartnershipTransactionStatus
import build.wallet.platform.clipboard.Clipboard
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.pricechart.ChartType
import build.wallet.recovery.socrec.PostSocRecTaskRepository
import build.wallet.recovery.socrec.SocRecService
import build.wallet.statemachine.cloud.health.RepairCloudBackupStateMachine
import build.wallet.statemachine.cloud.health.RepairMobileKeyBackupProps
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.list.ListFormBodyModel
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.LostHardwareRecoveryInProgressData
import build.wallet.statemachine.fwup.FwupNfcUiProps
import build.wallet.statemachine.fwup.FwupNfcUiStateMachine
import build.wallet.statemachine.inheritance.*
import build.wallet.statemachine.inheritance.claims.complete.CompleteInheritanceClaimUiStateMachine
import build.wallet.statemachine.inheritance.claims.complete.CompleteInheritanceClaimUiStateMachineProps
import build.wallet.statemachine.limit.SetSpendingLimitUiStateMachine
import build.wallet.statemachine.limit.SpendingLimitProps
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.*
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingBalanceUiState.BottomSheetDisplayState.*
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingTransactionUiState.EntryPoint
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingTransactionUiState.EntryPoint.ACTIVITY
import build.wallet.statemachine.moneyhome.full.MoneyHomeUiState.ViewingTransactionUiState.EntryPoint.BALANCE
import build.wallet.statemachine.partnerships.AddBitcoinBottomSheetDisplayState
import build.wallet.statemachine.partnerships.purchase.CustomAmountEntryUiProps
import build.wallet.statemachine.partnerships.purchase.CustomAmountEntryUiStateMachine
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
import build.wallet.statemachine.recovery.socrec.inviteflow.InviteTrustedContactFlowUiProps
import build.wallet.statemachine.recovery.socrec.inviteflow.InviteTrustedContactFlowUiStateMachine
import build.wallet.statemachine.recovery.sweep.SweepUiProps
import build.wallet.statemachine.recovery.sweep.SweepUiStateMachine
import build.wallet.statemachine.send.SendUiProps
import build.wallet.statemachine.send.SendUiStateMachine
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsProps
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiStateMachine
import build.wallet.statemachine.transactions.*
import build.wallet.statemachine.transactions.TransactionsActivityProps.TransactionVisibility.All
import build.wallet.statemachine.utxo.UtxoConsolidationProps
import build.wallet.statemachine.utxo.UtxoConsolidationUiStateMachine
import com.github.michaelbull.result.get
import kotlinx.coroutines.launch
import build.wallet.statemachine.settings.full.device.fingerprints.EntryPoint as FingerprintManagementEntryPoint

@BitkeyInject(ActivityScope::class)
class MoneyHomeUiStateMachineImpl(
  private val addressQrCodeUiStateMachine: AddressQrCodeUiStateMachine,
  private val sendUiStateMachine: SendUiStateMachine,
  private val transactionDetailsUiStateMachine: TransactionDetailsUiStateMachine,
  private val transactionsActivityUiStateMachine: TransactionsActivityUiStateMachine,
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
  private val bitcoinPriceChartUiStateMachine: BitcoinPriceChartUiStateMachine,
  private val socRecService: SocRecService,
  private val utxoConsolidationUiStateMachine: UtxoConsolidationUiStateMachine,
  private val partnershipsSellUiStateMachine: PartnershipsSellUiStateMachine,
  private val failedPartnerTransactionUiStateMachine: FailedPartnerTransactionUiStateMachine,
  private val inheritanceMarketingFlag: InheritanceMarketingFeatureFlag,
  private val inheritanceManagementUiStateMachine: InheritanceManagementUiStateMachine,
  private val inheritanceUpsellService: InheritanceUpsellService,
  private val completeClaimUiStateMachine: CompleteInheritanceClaimUiStateMachine,
  private val declineInheritanceClaimUiStateMachine: DeclineInheritanceClaimUiStateMachine,
  private val onboardingCompletionService: OnboardingCompletionService,
) : MoneyHomeUiStateMachine {
  @Composable
  override fun model(props: MoneyHomeUiProps): ScreenModel {
    val justCompletingSocialRecovery by remember {
      socRecService.justCompletedRecovery()
    }.collectAsState(initial = false)

    val shouldShowUpsell = remember { mutableStateOf(false) }

    val scope = rememberStableCoroutineScope()

    LaunchedEffect("check-for-upsell") {
      // Ensure onboarding is recorded for users who completed it before
      // this feature was introduced
      shouldShowUpsell.value = inheritanceUpsellService.shouldShowUpsell()
      onboardingCompletionService.recordCompletionIfNotExists()
    }

    var uiState: MoneyHomeUiState by remember(
      props.origin,
      justCompletingSocialRecovery,
      shouldShowUpsell.value
    ) {
      val initialState = when (val origin = props.origin) {
        MoneyHomeUiProps.Origin.Launch -> {
          // Navigate directly to hardware recovery when completing hardware recovery
          val lostHardwareRecoveryData = props.lostHardwareRecoveryData

          when {
            justCompletingSocialRecovery -> {
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
            inheritanceMarketingFlag.isEnabled() && shouldShowUpsell.value -> {
              scope.launch {
                inheritanceUpsellService.markUpsellAsSeen()
              }
              ViewingBalanceUiState(
                bottomSheetDisplayState = InheritanceUpsell
              )
            }
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
        else -> ViewingBalanceUiState()
      }
      mutableStateOf(initialState)
    }

    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()

    return when (val state = uiState) {
      is FixingCloudBackupState -> repairCloudBackupStateMachine.model(
        RepairMobileKeyBackupProps(
          account = props.account as FullAccount,
          presentationStyle = Modal,
          mobileKeyBackupStatus = state.status,
          onExit = { uiState = ViewingBalanceUiState() },
          onRepaired = { uiState = ViewingBalanceUiState() }
        )
      )

      is ViewingBalanceUiState -> moneyHomeViewingBalanceUiStateMachine.model(
        props = MoneyHomeViewingBalanceUiProps(
          account = props.account,
          lostHardwareRecoveryData = props.lostHardwareRecoveryData,
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
          presentationStyle = ScreenPresentationStyle.ModalFullScreen,
          onExit = { uiState = ViewingBalanceUiState() },
          onSuccess = { uiState = ViewingBalanceUiState() },
          recoveredFactor = null,
          keybox = (props.account as FullAccount).keybox
        )
      )

      SellFlowUiState -> partnershipsSellUiStateMachine.model(
        props = PartnershipsSellUiProps(
          account = props.account as FullAccount,
          onBack = { uiState = ViewingBalanceUiState() }
        )
      )

      ReceiveFlowUiState -> ReceiveBitcoinModel(
        props,
        onExit = {
          uiState = ViewingBalanceUiState()
        }
      )

      is SendFlowUiState -> SendBitcoinModel(
        props,
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
        lostHardwareRecoveryData = props.lostHardwareRecoveryData,
        instructionsStyle = state.instructionsStyle,
        onExit = {
          uiState = ViewingBalanceUiState()
        }
      )

      is FwupFlowUiState -> fwupNfcUiStateMachine.model(
        props =
          FwupNfcUiProps(
            firmwareData = state.firmwareData,
            isHardwareFake = (props.account as FullAccount).config.isHardwareFake,
            onDone = {
              uiState = ViewingBalanceUiState()
            }
          )
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

      is InviteTrustedContactFlow -> inviteTrustedContactFlowUiStateMachine.model(
        props = InviteTrustedContactFlowUiProps(
          account = props.account as FullAccount,
          onExit = { uiState = ViewingBalanceUiState() }
        )
      )

      is SelectCustomPartnerPurchaseAmountState -> customAmountEntryUiStateMachine.model(
        props = CustomAmountEntryUiProps(
          minimumAmount = state.minimumAmount,
          maximumAmount = state.maximumAmount,
          onBack = {
            uiState =
              ViewingBalanceUiState(
                bottomSheetDisplayState =
                  Partners(
                    initialState = AddBitcoinBottomSheetDisplayState.PurchasingUiState(
                      selectedAmount = FiatMoney.zero(fiatCurrency)
                    )
                  )
              )
          },
          onNext = { selectedAmount ->
            uiState =
              ViewingBalanceUiState(
                bottomSheetDisplayState =
                  Partners(
                    initialState = AddBitcoinBottomSheetDisplayState.PurchasingUiState(
                      selectedAmount = selectedAmount
                    )
                  )
              )
          }
        )
      )

      AddAdditionalFingerprintUiState -> managingFingerprintsUiStateMachine.model(
        ManagingFingerprintsProps(
          account = props.account as FullAccount,
          onBack = { uiState = ViewingBalanceUiState() },
          onFwUpRequired = {
            uiState = ViewingBalanceUiState(bottomSheetDisplayState = PromptingForFwUpUiState)
          },
          entryPoint = FingerprintManagementEntryPoint.MONEY_HOME
        )
      )
      is ShowingPriceChartUiState -> bitcoinPriceChartUiStateMachine.model(
        BitcoinPriceChartUiProps(
          initialType = state.type,
          accountId = props.account.accountId,
          f8eEnvironment = props.account.config.f8eEnvironment,
          onBack = { uiState = ViewingBalanceUiState() }
        )
      )

      ConsolidatingUtxosUiState -> utxoConsolidationUiStateMachine.model(
        props = UtxoConsolidationProps(
          onConsolidationSuccess = { uiState = ViewingBalanceUiState() },
          onBack = { uiState = ViewingBalanceUiState() }
        )
      )
      is ConfirmingPartnerSale -> partnershipsSellUiStateMachine.model(
        props = PartnershipsSellUiProps(
          account = props.account as FullAccount,
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
          onBeneficiaryRemoved = { uiState = ViewingBalanceUiState() },
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

      is InheritanceManagementUiState -> {
        inheritanceManagementUiStateMachine.model(
          props = InheritanceManagementUiProps(
            account = props.account as FullAccount,
            selectedTab = state.selectedTab,
            onBack = { uiState = ViewingBalanceUiState() },
            onGoToUtxoConsolidation = { uiState = ConsolidatingUtxosUiState }
          )
        )
      }
    }
  }

  @Composable
  private fun ReceiveBitcoinModel(
    props: MoneyHomeUiProps,
    onExit: () -> Unit,
  ) = addressQrCodeUiStateMachine.model(
    props =
      AddressQrCodeUiProps(
        account = props.account as FullAccount,
        onBack = onExit
      )
  ).asModalFullScreen()

  @Composable
  private fun SendBitcoinModel(
    props: MoneyHomeUiProps,
    validPaymentDataInClipboard: ParsedPaymentData?,
    onExit: () -> Unit,
    onGoToUtxoConsolidation: () -> Unit,
  ) = sendUiStateMachine.model(
    props = SendUiProps(
      account = props.account as FullAccount,
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
  private fun HardwareRecoveryModel(
    account: FullAccount,
    lostHardwareRecoveryData: LostHardwareRecoveryData,
    instructionsStyle: InstructionsStyle,
    onExit: () -> Unit,
  ): ScreenModel {
    val scope = rememberStableCoroutineScope()
    return lostHardwareUiStateMachine.model(
      props =
        LostHardwareRecoveryProps(
          account = account,
          lostHardwareRecoveryData = lostHardwareRecoveryData,
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
}

sealed interface MoneyHomeUiState {
  /**
   * Indicates that we are viewing the balance, with nothing presented on top
   */
  data class ViewingBalanceUiState(
    val isRefreshing: Boolean = false,
    val bottomSheetDisplayState: BottomSheetDisplayState? = null,
    val urlStringForInAppBrowser: Boolean = false,
    val selectedContact: TrustedContact? = null,
  ) : MoneyHomeUiState {
    sealed interface BottomSheetDisplayState {
      /**
       * We have entered the partners flow, which is a half-sheet
       * displayed on top of the money home screen
       */
      data class Partners(
        val initialState: AddBitcoinBottomSheetDisplayState,
      ) : BottomSheetDisplayState

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

      /**
       * Showing the inheritance upsell modal
       */
      data object InheritanceUpsell : BottomSheetDisplayState
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
   * Indicates that we are in the process of fixing cloud backup state.
   */
  data class FixingCloudBackupState(
    val status: MobileKeyBackupStatus.ProblemWithBackup,
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
   * Inheritance management flow presented after the upsell modal
   *
   * @property selectedTab - the tab to display
   */
  data class InheritanceManagementUiState(
    val selectedTab: ManagingInheritanceTab,
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
}
