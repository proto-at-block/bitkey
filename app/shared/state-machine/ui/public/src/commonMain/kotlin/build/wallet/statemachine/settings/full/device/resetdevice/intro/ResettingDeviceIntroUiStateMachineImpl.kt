package build.wallet.statemachine.settings.full.device.resetdevice.intro

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.encrypt.SignatureVerifier
import build.wallet.encrypt.verifyEcdsaResult
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.AuthF8eClient
import build.wallet.ktor.result.HttpError
import build.wallet.limit.MobilePayService
import build.wallet.limit.MobilePayStatus
import build.wallet.limit.SpendingLimit
import build.wallet.logging.LogLevel
import build.wallet.logging.log
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
import build.wallet.statemachine.settings.full.device.resetdevice.ResettingDeviceEventTrackerScreenId
import build.wallet.statemachine.settings.full.device.resetdevice.intro.ResettingDeviceIntroUiState.*
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.*
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import okio.ByteString.Companion.encodeUtf8
import kotlin.time.Duration.Companion.milliseconds

class ResettingDeviceIntroUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val signatureVerifier: SignatureVerifier,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val currencyConverter: CurrencyConverter,
  private val mobilePayService: MobilePayService,
  private val authF8eClient: AuthF8eClient,
) : ResettingDeviceIntroUiStateMachine {
  @Composable
  override fun model(props: ResettingDeviceIntroProps): ScreenModel {
    var uiState: ResettingDeviceIntroUiState by remember {
      mutableStateOf(
        IntroState()
      )
    }

    val scope = rememberStableCoroutineScope()

    return when (val state = uiState) {
      is IntroState -> {
        if (state.shouldUnwindToMoneyHome) {
          LaunchedEffect("unwind-to-money-home") {
            delay(750.milliseconds)
            props.onUnwindToMoneyHome()
          }
        }
        ResettingDeviceIntroModel(
          presentedModally = props.fullAccount != null,
          onBack = props.onBack,
          onResetDevice = { uiState = ScanToContinueState },
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

        ResettingDeviceIntroModel(
          presentedModally = props.fullAccount != null,
          onBack = props.onBack,
          onResetDevice = { uiState = ScanToContinueState },
          bottomSheet = bottomSheet
        )
      }

      is ScanningState -> {
        InitialDeviceTapModel(
          pubKey = props.fullAccount?.keybox?.activeHwKeyBundle?.authKey?.pubKey,
          balance = props.balance,
          isHardwareFake = props.fullAccountConfig.isHardwareFake,
          onTapPairedDevice = { balance ->
            if (balance.untrustedPending.isPositive) {
              // Incoming pending transaction, treat as spendable and send to
              // transfer funds before reset sheet
              uiState = TransferringFundsState
            } else if (balance.spendable.isPositive && props.spendingWallet != null) {
              // Spendable balance, check if it's actually spendable
              scope.launch {
                props.spendingWallet.isBalanceSpendable()
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
          onTapUnknownDevice = {
            uiState = UnpairedDeviceWarningState(it)
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

      is UnpairedDeviceWarningState -> {
        var isActive by remember { mutableStateOf(true) }
        var isLoading by remember { mutableStateOf(true) }

        val hwAuthPubKey = HwAuthPublicKey(state.publicKey)
        LaunchedEffect(state.publicKey) {
          isActive = isDeviceActive(
            f8eEnvironment = props.fullAccountConfig.f8eEnvironment,
            hwAuthPubKey = hwAuthPubKey
          )
          isLoading = false
        }

        val bottomSheet = if (isLoading) {
          null
        } else {
          UnpairedDeviceWarningSheet(
            isDeviceActive = isActive,
            onResetDevice = {
              props.onDeviceConfirmed(false)
            },
            onCancel = { uiState = IntroState() }
          )
        }

        ResettingDeviceIntroModel(
          presentedModally = props.fullAccount != null,
          onBack = props.onBack,
          onResetDevice = { uiState = ScanToContinueState },
          bottomSheet = bottomSheet
        )
      }

      is TransferringFundsState -> {
        // It's impossible to get here without a balance and a non-null fullAccount
        ResettingDeviceIntroModel(
          presentedModally = props.fullAccount != null,
          onBack = props.onBack,
          onResetDevice = { uiState = ScanToContinueState },
          bottomSheet = TransferFundsBeforeResetSheet(
            onTransferFunds = {
              uiState = IntroState(shouldUnwindToMoneyHome = true)
            },
            onCancel = { uiState = IntroState() },
            balance = props.balance!!,
            account = props.fullAccount!!
          )
        )
      }
    }
  }

  @Composable
  fun ResettingDeviceIntroModel(
    presentedModally: Boolean,
    onBack: () -> Unit,
    onResetDevice: () -> Unit,
    bottomSheet: SheetModel? = null,
  ): ScreenModel {
    val resettingDeviceModel = FormBodyModel(
      id = ResettingDeviceEventTrackerScreenId.RESET_DEVICE_INTRO,
      onBack = null,
      toolbar = ToolbarModel(
        leadingAccessory = if (presentedModally) {
          ToolbarAccessoryModel.IconAccessory.CloseAccessory(onBack)
        } else {
          ToolbarAccessoryModel.IconAccessory.BackAccessory(onBack)
        }
      ),
      header = FormHeaderModel(
        headline = "Reset your Bitkey device",
        subline = "Reset your Bitkey device so it can be safely discarded, traded in, or given away."
      ),
      mainContentList = immutableListOf(
        FormMainContentModel.ListGroup(
          listGroupModel = ListGroupModel(
            header = "These items will be removed.",
            headerTreatment = ListGroupModel.HeaderTreatment.PRIMARY,
            items = immutableListOf(
              ListItemModel(
                leadingAccessory = ListItemAccessory.IconAccessory(
                  model = IconModel(
                    icon = Icon.SmallIconKey,
                    iconSize = IconSize.Small,
                    iconOpacity = 0.30f
                  )
                ),
                title = "Device key"
              ),
              ListItemModel(
                leadingAccessory = ListItemAccessory.IconAccessory(
                  model = IconModel(
                    icon = Icon.SmallIconFingerprint,
                    iconSize = IconSize.Small,
                    iconTint = IconTint.On30
                  )
                ),
                title = "Saved fingerprints"
              )
            ),
            style = ListGroupStyle.CARD_GROUP_DIVIDER
          )
        )
      ),
      primaryButton = ButtonModel(
        text = "Reset device",
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.Secondary,
        onClick = StandardClick { onResetDevice() }
      )
    )

    return ScreenModel(
      body = resettingDeviceModel,
      bottomSheetModel = bottomSheet,
      presentationStyle = ScreenPresentationStyle.Root
    )
  }

  @Composable
  private fun ScanToContinueSheet(
    onScanToContinue: () -> Unit,
    onClose: () -> Unit,
  ): SheetModel {
    return SheetModel(
      size = SheetSize.DEFAULT,
      onClosed = onClose,
      body = FormBodyModel(
        id = ResettingDeviceEventTrackerScreenId.RESET_DEVICE_SCAN_SHEET,
        onBack = onClose,
        toolbar = null,
        header = FormHeaderModel(
          headline = "Scan your Bitkey device",
          subline = "This will not reset the device."
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
    )
  }

  @Composable
  private fun InitialDeviceTapModel(
    pubKey: Secp256k1PublicKey?,
    balance: BitcoinBalance?,
    isHardwareFake: Boolean,
    onTapPairedDevice: (spendableBalance: BitcoinBalance) -> Unit,
    onTapUnknownDevice: (pubKey: Secp256k1PublicKey) -> Unit,
    onCancel: () -> Unit,
  ): ScreenModel {
    val challengeString = "verify-paired-device".encodeUtf8()

    return nfcSessionUIStateMachine.model(
      NfcSessionUIStateMachineProps(
        session = { session, commands ->
          // if we're not onbaorded, we need to get the pubKey from the hardware
          val hwPubKey = pubKey ?: commands.getAuthenticationKey(session).pubKey
          val signature = commands.signChallenge(session, challengeString)
          Pair(hwPubKey, signature)
        },
        onSuccess = { pair ->
          val verification = signatureVerifier.verifyEcdsaResult(
            message = challengeString,
            signature = pair.second,
            publicKey = pair.first
          )
          if (verification.get() == true && balance != null) {
            onTapPairedDevice(balance)
          } else {
            onTapUnknownDevice(pair.first)
          }
        },
        onCancel = onCancel,
        isHardwareFake = isHardwareFake,
        screenPresentationStyle = ScreenPresentationStyle.Modal,
        shouldLock = false,
        eventTrackerContext = NfcEventTrackerScreenIdContext.HW_PROOF_OF_POSSESSION
      )
    )
  }

  @Composable
  private fun TransferFundsBeforeResetSheet(
    balance: BitcoinBalance,
    account: FullAccount,
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
      when (val mobilePayStatus = mobilePayService.status(account).firstOrNull()) {
        is MobilePayStatus.MobilePayEnabled -> {
          spendingLimit = mobilePayStatus.activeSpendingLimit
        }
        else -> Unit
      }
    }

    return SheetModel(
      size = SheetSize.DEFAULT,
      onClosed = onCancel,
      body = FormBodyModel(
        id = ResettingDeviceEventTrackerScreenId.RESET_DEVICE_TRANSFER_FUNDS,
        onBack = onCancel,
        toolbar = null,
        header = FormHeaderModel(
          headline = "Transfer funds before you reset the device",
          subline = when (val limit = spendingLimit) {
            null -> "Once reset, you won’t be able to transfer funds above your mobile limit."
            else ->
              "Once reset, you won’t be able to transfer funds above ${
                moneyDisplayFormatter.format(
                  limit.amount
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
                  title = when (val fiatBal = fiatBalance) {
                    null -> moneyDisplayFormatter.format(balance.total)
                    else -> moneyDisplayFormatter.format(fiatBal)
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
    )
  }

  @Composable
  private fun SpendableBalanceCheckErrorModel(
    onRetry: () -> Unit,
    onCancel: () -> Unit,
  ): ScreenModel {
    return ScreenModel(
      body = FormBodyModel(
        id = ResettingDeviceEventTrackerScreenId.RESET_DEVICE_BALANCE_CHECK_ERROR,
        onBack = onCancel,
        toolbar = ToolbarModel(
          leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(onCancel)
        ),
        header = FormHeaderModel(
          icon = Icon.LargeIconWarningFilled,
          headline = "We’re having trouble loading your device details",
          subline = "You can continue to reset your device or try again."
        ),
        primaryButton = ButtonModel(
          text = "Try again",
          onClick = StandardClick(onRetry),
          size = ButtonModel.Size.Footer,
          treatment = ButtonModel.Treatment.Primary
        ),
        secondaryButton = ButtonModel(
          text = "Reset device",
          onClick = StandardClick(onCancel),
          size = ButtonModel.Size.Footer,
          treatment = ButtonModel.Treatment.Secondary
        )
      ),
      presentationStyle = ScreenPresentationStyle.ModalFullScreen
    )
  }

  @Composable
  private fun UnpairedDeviceWarningSheet(
    isDeviceActive: Boolean,
    onResetDevice: () -> Unit,
    onCancel: () -> Unit,
  ): SheetModel {
    val subline = if (isDeviceActive) {
      "This device might be protecting funds. If you reset the device, the funds may no longer be accessible."
    } else {
      "You can still safely reset the device, since there aren’t any funds on it."
    }

    return SheetModel(
      size = SheetSize.DEFAULT,
      onClosed = onCancel,
      body = FormBodyModel(
        id = ResettingDeviceEventTrackerScreenId.RESET_DEVICE_CONFIRMATION,
        onBack = onCancel,
        toolbar = null,
        header = FormHeaderModel(
          headline = "This Bitkey device isn’t paired to this app",
          subline = subline
        ),
        primaryButton = ButtonModel(
          text = "Reset device",
          requiresBitkeyInteraction = false,
          onClick = onResetDevice,
          size = ButtonModel.Size.Footer,
          treatment = ButtonModel.Treatment.PrimaryDanger
        ),
        secondaryButton = ButtonModel(
          text = "Cancel",
          treatment = ButtonModel.Treatment.Secondary,
          size = ButtonModel.Size.Footer,
          onClick = StandardClick(onCancel)
        ),
        renderContext = RenderContext.Sheet
      )
    )
  }

  @Suppress("TooGenericExceptionCaught")
  private suspend fun isDeviceActive(
    f8eEnvironment: F8eEnvironment,
    hwAuthPubKey: HwAuthPublicKey,
  ): Boolean {
    val response = authF8eClient.initiateHardwareAuthentication(
      f8eEnvironment = f8eEnvironment,
      authPublicKey = hwAuthPubKey
    )

    // Return true if the response status is 200, false if 404, otherwise true.
    // This is because in the true case, we inform the user that the device
    // *might* be protecting funds, and in the false case, we inform the user that the device
    // is *not* protecting any funds.
    return response.fold(
      success = { true },
      failure = { error ->
        when (error) {
          is HttpError.ClientError -> {
            when (error.response.status.value) {
              404 -> false
              else -> true
            }
          }
          else -> {
            log(LogLevel.Error, throwable = error) { "Error checking if device is active: $error" }
            true
          }
        }
      }
    )
  }
}

private sealed interface ResettingDeviceIntroUiState {
  /**
   * Viewing the reset device confirmation screen
   */
  data class IntroState(
    val shouldUnwindToMoneyHome: Boolean = false,
  ) : ResettingDeviceIntroUiState

  /**
   * Viewing the scan to continue bottom sheet
   */
  data object ScanToContinueState : ResettingDeviceIntroUiState

  /**
   * Scan to confirm device
   */
  data class ScanningState(val isScanning: Boolean) : ResettingDeviceIntroUiState

  /**
   * Error checking spendable balance
   */
  data object SpendableBalanceCheckFailedState : ResettingDeviceIntroUiState

  /**
   * Warning state for unpaired device
   */
  data class UnpairedDeviceWarningState(
    val publicKey: Secp256k1PublicKey,
  ) : ResettingDeviceIntroUiState

  /**
   * Viewing the transfer funds before reset bottom sheet
   */
  data object TransferringFundsState : ResettingDeviceIntroUiState
}
