package build.wallet.statemachine.settings.full.device.wipedevice.intro

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.limit.MobilePayData.MobilePayEnabledData
import build.wallet.limit.MobilePayService
import build.wallet.limit.SpendingLimit
import build.wallet.logging.logError
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceEventTrackerScreenId
import build.wallet.statemachine.settings.full.device.wipedevice.intro.WipingDeviceIntroUiState.*
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.*
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@BitkeyInject(ActivityScope::class)
class WipingDeviceIntroUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val currencyConverter: CurrencyConverter,
  private val mobilePayService: MobilePayService,
  private val bitcoinWalletService: BitcoinWalletService,
) : WipingDeviceIntroUiStateMachine {
  @Composable
  @Suppress("CyclomaticComplexMethod")
  override fun model(props: WipingDeviceIntroProps): ScreenModel {
    var uiState: WipingDeviceIntroUiState by remember {
      mutableStateOf(
        IntroState()
      )
    }

    val transactionsData =
      remember { bitcoinWalletService.transactionsData() }.collectAsState().value

    val scope = rememberStableCoroutineScope()

    return when (val state = uiState) {
      is IntroState -> {
        if (state.shouldUnwindToMoneyHome) {
          LaunchedEffect("unwind-to-money-home") {
            delay(750.milliseconds)
            props.onUnwindToMoneyHome()
          }
        }
        WipingDeviceIntroModel(
          onBack = props.onBack,
          onWipeDevice = { uiState = ScanToContinueState },
          bottomSheet = null
        )
      }

      is ScanToContinueState -> {
        val bottomSheet = ScanToContinueSheet(
          onScanToContinue = {
            uiState = ScanningState(isScanning = true)
          },
          onClose = { uiState = IntroState() }
        )

        WipingDeviceIntroModel(
          onBack = props.onBack,
          onWipeDevice = { uiState = ScanToContinueState },
          bottomSheet = bottomSheet
        )
      }

      is ScanningState -> {
        val spendingWallet = remember { bitcoinWalletService.spendingWallet() }
          .collectAsState()
          .value

        InitialDeviceTapModel(
          onTapPairedDevice = {
            val balance = transactionsData?.balance
            if (balance == null) {
              uiState = SpendableBalanceCheckFailedState
            } else if (balance.untrustedPending.isPositive) {
              // Incoming pending transaction, treat as spendable and send to
              // transfer funds before wipe sheet
              uiState = TransferringFundsState
            } else if (balance.spendable.isPositive && spendingWallet != null) {
              // Spendable balance, check if it's actually spendable
              scope.launch {
                spendingWallet.isBalanceSpendable()
                  .onSuccess { isSpendable ->
                    if (isSpendable) {
                      uiState = TransferringFundsState
                    } else {
                      props.onDeviceConfirmed(true)
                    }
                  }.onFailure {
                    uiState = SpendableBalanceCheckFailedState
                  }
              }
            } else {
              props.onDeviceConfirmed(true)
            }
          },
          onCancel = {
            uiState = IntroState()
          }
        )
      }

      is SpendableBalanceCheckFailedState -> {
        SpendableBalanceCheckErrorModel(
          onRetry = {
            uiState = ScanningState(isScanning = true)
          },
          onCancel = {
            uiState = IntroState()
          }
        )
      }

      is TransferringFundsState -> {
        val shouldNotReach = transactionsData?.balance == null
        LaunchedEffect("log-should-not-reach-TransferringFundsState", shouldNotReach) {
          if (shouldNotReach) {
            logError {
              "WipingDeviceIntroUiStateMachineImpl.TransferringFundsState reached without a balance or fullAccount! This should never happen"
            }
          }
        }

        WipingDeviceIntroModel(
          onBack = props.onBack,
          onWipeDevice = { uiState = ScanToContinueState },
          bottomSheet = TransferFundsBeforeWipeSheet(
            onTransferFunds = {
              uiState = IntroState(shouldUnwindToMoneyHome = true)
            },
            onCancel = { uiState = IntroState() },
            balance = transactionsData!!.balance
          )
        )
      }
    }
  }

  @Composable
  fun WipingDeviceIntroModel(
    onBack: () -> Unit,
    onWipeDevice: () -> Unit,
    bottomSheet: SheetModel? = null,
  ): ScreenModel {
    val wipingDeviceModel = WipingDeviceIntroBodyModel(
      onBack = onBack,
      onWipeDevice = onWipeDevice
    )

    return ScreenModel(
      body = wipingDeviceModel,
      bottomSheetModel = bottomSheet,
      presentationStyle = ScreenPresentationStyle.Root
    )
  }

  private data class WipingDeviceIntroBodyModel(
    override val onBack: () -> Unit,
    val onWipeDevice: () -> Unit,
  ) : FormBodyModel(
      id = WipingDeviceEventTrackerScreenId.RESET_DEVICE_INTRO,
      onBack = null,
      toolbar = ToolbarModel(
        leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(onBack)
      ),
      header = FormHeaderModel(
        headline = "Permanently wipe your device",
        subline = "Always transfer the funds from this wallet to a new wallet before wiping this device.\n\n" +
          "Wiping a device can lead to permanent loss of funds."
      ),
      primaryButton = ButtonModel(
        text = "Wipe device",
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.Secondary,
        onClick = StandardClick { onWipeDevice() }
      )
    )

  @Composable
  private fun ScanToContinueSheet(
    onScanToContinue: () -> Unit,
    onClose: () -> Unit,
  ): SheetModel {
    return SheetModel(
      size = SheetSize.DEFAULT,
      onClosed = onClose,
      body = ScanToContinueSheetModel(
        onScanToContinue = onScanToContinue,
        onClose = onClose
      )
    )
  }

  private data class ScanToContinueSheetModel(
    val onClose: () -> Unit,
    val onScanToContinue: () -> Unit,
  ) : FormBodyModel(
      id = WipingDeviceEventTrackerScreenId.RESET_DEVICE_SCAN_SHEET,
      onBack = onClose,
      toolbar = null,
      header = FormHeaderModel(
        headline = "Scan your Bitkey"
      ),
      primaryButton = ButtonModel(
        text = "Scan to continue",
        requiresBitkeyInteraction = true,
        onClick = onScanToContinue,
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.Primary
      ),
      secondaryButton = ButtonModel(
        text = "Cancel",
        treatment = ButtonModel.Treatment.Secondary,
        size = ButtonModel.Size.Footer,
        onClick = StandardClick(onClose)
      ),
      renderContext = RenderContext.Sheet
    )

  @Composable
  private fun InitialDeviceTapModel(
    onTapPairedDevice: () -> Unit,
    onCancel: () -> Unit,
  ): ScreenModel {
    return nfcSessionUIStateMachine.model(
      NfcSessionUIStateMachineProps(
        session = { session, commands ->
          // Any command will result in an nfc interceptor validating that the hardware is paired; we
          // just need to choose one that requires the device to be unlocked.
          commands.getDeviceInfo(session)
        },
        onSuccess = { onTapPairedDevice() },
        onCancel = onCancel,
        screenPresentationStyle = ScreenPresentationStyle.Modal,
        eventTrackerContext = NfcEventTrackerScreenIdContext.HW_PROOF_OF_POSSESSION
      )
    )
  }

  @Composable
  private fun TransferFundsBeforeWipeSheet(
    balance: BitcoinBalance,
    onTransferFunds: () -> Unit,
    onCancel: () -> Unit,
  ): SheetModel {
    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()
    var fiatBalance: FiatMoney? by remember { mutableStateOf(null) }
    var spendingLimit: SpendingLimit? by remember { mutableStateOf(null) }

    LaunchedEffect(
      "sync-fiat-equivalent-balance-and-spending-limit",
      balance.total,
      fiatCurrency
    ) {
      // Sync fiat balance
      val convertedFiatBalance = currencyConverter
        .convert(
          fromAmount = balance.total,
          toCurrency = fiatCurrency,
          atTime = null
        ).filterNotNull().firstOrNull() as? FiatMoney
        ?: FiatMoney.zero(fiatCurrency)

      fiatBalance = convertedFiatBalance

      // Get active spending limit
      when (val mobilePayData = mobilePayService.mobilePayData.firstOrNull()) {
        is MobilePayEnabledData -> {
          spendingLimit = mobilePayData.activeSpendingLimit
        }
        else -> Unit
      }
    }

    return SheetModel(
      size = SheetSize.DEFAULT,
      onClosed = onCancel,
      body = TransferFundsBeforeWipeSheetBodyModel(
        balance = balance,
        onTransferFunds = onTransferFunds,
        onCancel = onCancel,
        spendingLimit = spendingLimit,
        fiatBalance = fiatBalance,
        moneyDisplayFormatter = moneyDisplayFormatter
      )
    )
  }

  private data class TransferFundsBeforeWipeSheetBodyModel(
    val balance: BitcoinBalance,
    val fiatBalance: FiatMoney?,
    val spendingLimit: SpendingLimit?,
    val onTransferFunds: () -> Unit,
    val onCancel: () -> Unit,
    val moneyDisplayFormatter: MoneyDisplayFormatter,
  ) : FormBodyModel(
      id = WipingDeviceEventTrackerScreenId.RESET_DEVICE_TRANSFER_FUNDS,
      onBack = onCancel,
      toolbar = null,
      header = FormHeaderModel(
        headline = "Transfer funds before you wipe the device",
        subline = when (spendingLimit) {
          null -> "Once wiped, you won’t be able to transfer funds above your mobile limit."
          else ->
            "Once wiped, you won’t be able to transfer funds above ${
              moneyDisplayFormatter.format(
                spendingLimit.amount
              )
            } mobile limit."
        }
      ),
      mainContentList = immutableListOf(
        FormMainContentModel.ListGroup(
          listGroupModel = ListGroupModel(
            header = "Your funds",
            headerTreatment = ListGroupModel.HeaderTreatment.PRIMARY,
            items = immutableListOf(
              ListItemModel(
                title = when (fiatBalance) {
                  null -> moneyDisplayFormatter.format(balance.total)
                  else -> moneyDisplayFormatter.format(fiatBalance)
                },
                titleAlignment = ListItemTitleAlignment.CENTER,
                treatment = ListItemTreatment.SECONDARY_DISPLAY,
                secondaryText = if (fiatBalance != null) {
                  moneyDisplayFormatter.format(balance.total)
                } else {
                  ""
                }
              )
            ),
            style = ListGroupStyle.CARD_GROUP
          )
        )
      ),
      primaryButton = ButtonModel(
        text = "Transfer funds",
        requiresBitkeyInteraction = false,
        onClick = onTransferFunds,
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.Primary
      ),
      secondaryButton = ButtonModel(
        text = "Cancel",
        treatment = ButtonModel.Treatment.Secondary,
        size = ButtonModel.Size.Footer,
        onClick = StandardClick(onCancel)
      ),
      renderContext = RenderContext.Sheet
    )

  @Composable
  private fun SpendableBalanceCheckErrorModel(
    onRetry: () -> Unit,
    onCancel: () -> Unit,
  ): ScreenModel {
    return ScreenModel(
      body = SpendableBalanceCheckErrorBodyModel(
        onRetry = onRetry,
        onCancel = onCancel
      ),
      presentationStyle = ScreenPresentationStyle.ModalFullScreen
    )
  }

  private data class SpendableBalanceCheckErrorBodyModel(
    val onRetry: () -> Unit,
    val onCancel: () -> Unit,
  ) : FormBodyModel(
      id = WipingDeviceEventTrackerScreenId.RESET_DEVICE_BALANCE_CHECK_ERROR,
      onBack = onCancel,
      toolbar = ToolbarModel(
        leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(onCancel)
      ),
      header = FormHeaderModel(
        icon = Icon.LargeIconWarningFilled,
        headline = "We’re having trouble loading your device details",
        subline = "You can continue to wipe your device or try again."
      ),
      primaryButton = ButtonModel(
        text = "Try again",
        onClick = StandardClick(onRetry),
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.Primary
      ),
      secondaryButton = ButtonModel(
        text = "Wipe device",
        onClick = StandardClick(onCancel),
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.Secondary
      )
    )
}

private sealed interface WipingDeviceIntroUiState {
  /**
   * Viewing the wipe device confirmation screen
   */
  data class IntroState(
    val shouldUnwindToMoneyHome: Boolean = false,
  ) : WipingDeviceIntroUiState

  /**
   * Viewing the scan to continue bottom sheet
   */
  data object ScanToContinueState : WipingDeviceIntroUiState

  /**
   * Scan to confirm device
   */
  data class ScanningState(val isScanning: Boolean) : WipingDeviceIntroUiState

  /**
   * Error checking spendable balance
   */
  data object SpendableBalanceCheckFailedState : WipingDeviceIntroUiState

  /**
   * Viewing the transfer funds before wipe bottom sheet
   */
  data object TransferringFundsState : WipingDeviceIntroUiState
}
