package build.wallet.statemachine.settings.full.device.wipedevice.intro

import androidx.compose.runtime.*
import bitkey.account.HardwareType
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.compose.collections.immutableListOf
import build.wallet.device.wipe.DeviceWipeEligibility.ActiveHasFunds
import build.wallet.device.wipe.DeviceWipeEligibility.ActiveReady
import build.wallet.device.wipe.DeviceWipeEligibility.InactiveHasFunds
import build.wallet.device.wipe.DeviceWipeEligibility.InactiveReady
import build.wallet.device.wipe.DeviceWipeEligibilityError.OldDeviceCheckFailed
import build.wallet.device.wipe.DeviceWipeEligibilityError.OldDevicePendingActiveTransaction
import build.wallet.device.wipe.DeviceWipeEligibilityError.OldDeviceSweepPendingConfirmation
import build.wallet.device.wipe.DeviceWipeEligibilityError.PairedDeviceBalanceCheckFailed
import build.wallet.device.wipe.DeviceWipeEligibilityError.UnknownDevice
import build.wallet.device.wipe.DeviceWipeEligibilityService
import build.wallet.device.wipe.TappedDeviceIdentity
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.limit.MobilePayData.MobilePayEnabledData
import build.wallet.limit.MobilePayService
import build.wallet.limit.SpendingLimit
import build.wallet.logging.logWarn
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
import build.wallet.statemachine.settings.full.device.wipedevice.ScanDeviceToWipeSheetBodyModel
import build.wallet.statemachine.settings.full.device.wipedevice.WipeContext
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceEventTrackerScreenId
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceInitialStep
import build.wallet.statemachine.settings.full.device.wipedevice.intro.WipingDeviceIntroUiState.*
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.*
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.fold
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlin.time.Duration.Companion.milliseconds

@BitkeyInject(ActivityScope::class)
class WipingDeviceIntroUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val deviceWipeEligibilityService: DeviceWipeEligibilityService,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val currencyConverter: CurrencyConverter,
  private val mobilePayService: MobilePayService,
) : WipingDeviceIntroUiStateMachine {
  @Composable
  @Suppress("CyclomaticComplexMethod")
  override fun model(props: WipingDeviceIntroProps): ScreenModel {
    var uiState: WipingDeviceIntroUiState by remember(props.initialStep) {
      mutableStateOf(
        when (props.initialStep) {
          WipingDeviceInitialStep.Intro -> IntroState()
          WipingDeviceInitialStep.ScanDevice -> ScanningState(isScanning = true)
        }
      )
    }

    fun cancelAfterScan() {
      when (props.initialStep) {
        WipingDeviceInitialStep.Intro -> uiState = IntroState()
        WipingDeviceInitialStep.ScanDevice -> props.onBack()
      }
    }

    fun retryScan() {
      uiState = ScanningState(isScanning = true)
    }

    return when (val state = uiState) {
      is IntroState -> {
        if (state.shouldUnwindToMoneyHome) {
          LaunchedEffect("unwind-to-money-home") {
            delay(750.milliseconds)
            props.onUnwindToMoneyHome()
          }
        }
        WipingDeviceIntroModel(
          presentedModally = props.fullAccount != null,
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
          presentedModally = props.fullAccount != null,
          onBack = props.onBack,
          onWipeDevice = { uiState = ScanToContinueState },
          bottomSheet = bottomSheet
        )
      }

      is ScanningState -> {
        if (props.fullAccount != null) {
          LoggedInDeviceClassificationModel(
            account = props.fullAccount,
            onDeviceClassified = { tappedDevice ->
              uiState = CheckingLoggedInDeviceState(
                account = props.fullAccount,
                tappedDevice = tappedDevice.identity()
              )
            },
            onAlreadyWipedOrNotSetUp = {
              uiState = AlreadyWipedOrNotSetUpState
            },
            onCancel = ::cancelAfterScan
          )
        } else {
          UnpairedDeviceTapModel(
            onTapDevice = {
              uiState = UnpairedDeviceWarningState
            },
            onCancel = ::cancelAfterScan
          )
        }
      }

      is CheckingLoggedInDeviceState -> {
        LaunchedEffect(state) {
          deviceWipeEligibilityService.evaluateLoggedInDevice(
            account = state.account,
            tappedDevice = state.tappedDevice
          ).fold(
            success = { eligibility ->
              when (eligibility) {
                ActiveReady ->
                  uiState = props.activeDeviceEligibilityState(ActiveDeviceEligibility.Ready)
                is ActiveHasFunds ->
                  uiState = props.activeDeviceEligibilityState(
                    ActiveDeviceEligibility.HasFunds(eligibility.balance)
                  )
                is InactiveReady ->
                  props.inactiveDeviceWipeContext(eligibility)?.let { wipeContext ->
                    props.onDeviceConfirmed(false, wipeContext)
                  } ?: run {
                    uiState = UnknownOldDeviceState
                  }
                is InactiveHasFunds ->
                  uiState = OldDeviceHasFundsState
              }
            },
            failure = { error ->
              uiState = when (error) {
                PairedDeviceBalanceCheckFailed -> SpendableBalanceCheckFailedState
                OldDevicePendingActiveTransaction,
                OldDeviceSweepPendingConfirmation -> OldDeviceNotReadyToWipeState
                OldDeviceCheckFailed -> OldDeviceCheckFailedState
                UnknownDevice -> UnknownOldDeviceState
              }
            }
          )
        }

        ScreenModel(
          body = LoadingBodyModel(
            id = WipingDeviceEventTrackerScreenId.RESET_DEVICE_CHECKING_ELIGIBILITY,
            title = "Checking device",
            description = "This should only take a moment.",
            onBack = ::cancelAfterScan
          ),
          presentationStyle = ScreenPresentationStyle.Modal
        )
      }

      is SpendableBalanceCheckFailedState -> {
        SpendableBalanceCheckErrorModel(
          onRetry = {
            retryScan()
          },
          onCancel = ::cancelAfterScan
        )
      }

      is UnpairedDeviceWarningState -> {
        val bottomSheet = UnpairedDeviceWarningSheet(
          onWipeDevice = {
            props.onDeviceConfirmed(false, WipeContext.Default)
          },
          onCancel = { uiState = IntroState() }
        )

        WipingDeviceIntroModel(
          presentedModally = props.fullAccount != null,
          onBack = props.onBack,
          onWipeDevice = { uiState = ScanToContinueState },
          bottomSheet = bottomSheet
        )
      }

      is ActiveDeviceInfoState -> {
        val transferFundsSheet = when {
          state.isShowingTransferFundsSheet &&
            state.eligibility is ActiveDeviceEligibility.HasFunds ->
            TransferFundsBeforeWipeSheet(
              onTransferFunds = {
                uiState = IntroState(shouldUnwindToMoneyHome = true)
              },
              onCancel = {
                uiState = state.copy(isShowingTransferFundsSheet = false)
              },
              balance = state.eligibility.balance
            )
          else -> null
        }

        ActiveDeviceInfoModel(
          onContinue = {
            when (state.eligibility) {
              ActiveDeviceEligibility.Ready ->
                props.onDeviceConfirmed(true, WipeContext.Default)
              is ActiveDeviceEligibility.HasFunds ->
                uiState = state.copy(isShowingTransferFundsSheet = true)
            }
          },
          onCancel = ::cancelAfterScan,
          bottomSheet = transferFundsSheet
        )
      }

      is ActiveDeviceTappedForOldWipeState -> {
        OldDeviceBlockingModel(
          id = WipingDeviceEventTrackerScreenId.RESET_DEVICE_OLD_DEVICE_UNKNOWN,
          headline = "This is your active Bitkey",
          subline = "Scan the first generation Bitkey you replaced during upgrade to wipe it.",
          onRetry = ::retryScan,
          onCancel = ::cancelAfterScan
        )
      }

      is OldDeviceNotReadyToWipeState -> {
        OldDeviceBlockingModel(
          id = WipingDeviceEventTrackerScreenId.RESET_DEVICE_OLD_DEVICE_PENDING_TRANSFER,
          headline = "Your first generation Bitkey device is not ready to wipe",
          subline = "Your sweep transaction is pending. Once it’s confirmed, you’ll be all set to wipe your device.",
          onRetry = ::retryScan,
          onCancel = ::cancelAfterScan
        )
      }

      is OldDeviceHasFundsState -> {
        OldDeviceBlockingModel(
          id = WipingDeviceEventTrackerScreenId.RESET_DEVICE_OLD_DEVICE_HAS_FUNDS,
          headline = "Transfer funds before wiping",
          subline = "We found funds that can still be transferred from this old Bitkey. " +
            "Complete the transfer, then try wiping it again.",
          onRetry = ::retryScan,
          onCancel = ::cancelAfterScan
        )
      }

      is UnknownOldDeviceState -> {
        OldDeviceBlockingModel(
          id = WipingDeviceEventTrackerScreenId.RESET_DEVICE_OLD_DEVICE_UNKNOWN,
          headline = "This Bitkey can’t be wiped from this wallet",
          subline = "The device you tapped isn’t paired with this wallet and doesn’t match a Bitkey previously used by this account.",
          onRetry = ::retryScan,
          onCancel = ::cancelAfterScan
        )
      }

      is OldDeviceCheckFailedState -> {
        OldDeviceBlockingModel(
          id = WipingDeviceEventTrackerScreenId.RESET_DEVICE_OLD_DEVICE_CHECK_FAILED,
          headline = "We’re having trouble loading your device details",
          subline = "Try again before wiping your old Bitkey.",
          onRetry = ::retryScan,
          onCancel = ::cancelAfterScan
        )
      }

      AlreadyWipedOrNotSetUpState -> {
        AlreadyWipedOrNotSetUpModel(onDone = props.onBack)
      }
    }
  }

  private fun WipingDeviceIntroProps.activeDeviceEligibilityState(
    eligibility: ActiveDeviceEligibility,
  ): WipingDeviceIntroUiState =
    when (wipeContext) {
      is WipeContext.InactiveDevice -> ActiveDeviceTappedForOldWipeState
      is WipeContext.Default -> ActiveDeviceInfoState(eligibility)
    }

  private fun WipingDeviceIntroProps.inactiveDeviceWipeContext(
    eligibility: InactiveReady,
  ): WipeContext.InactiveDevice? =
    when (val context = wipeContext) {
      is WipeContext.InactiveDevice -> context.takeIf { it.device == eligibility.device }
      is WipeContext.Default ->
        WipeContext.InactiveDevice(eligibility.device)
    }

  @Composable
  fun WipingDeviceIntroModel(
    presentedModally: Boolean = true,
    onBack: () -> Unit,
    onWipeDevice: () -> Unit,
    bottomSheet: SheetModel? = null,
  ): ScreenModel {
    val wipingDeviceModel = WipingDeviceIntroBodyModel(
      presentedModally = presentedModally,
      onBack = onBack,
      onWipeDevice = onWipeDevice
    )

    return ScreenModel(
      body = wipingDeviceModel,
      bottomSheetModel = bottomSheet,
      presentationStyle = ScreenPresentationStyle.Modal
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
      body = ScanDeviceToWipeSheetBodyModel(
        onScanToContinue = onScanToContinue,
        onBack = onClose
      )
    )
  }

  @Composable
  private fun LoggedInDeviceClassificationModel(
    account: FullAccount,
    onDeviceClassified: suspend (TappedDevice) -> Unit,
    onAlreadyWipedOrNotSetUp: () -> Unit,
    onCancel: () -> Unit,
  ): ScreenModel {
    return nfcSessionUIStateMachine.model(
      NfcSessionUIStateMachineProps(
        session = { session, commands ->
          commands.classifyTappedDevice(session, account)
        },
        onSuccess = onDeviceClassified,
        onCancel = onCancel,
        onError = { exception ->
          if (exception is NfcException.DeviceAlreadyWipedOrNotSetUp) {
            onAlreadyWipedOrNotSetUp()
            true
          } else {
            false
          }
        },
        hardwareVerification = NotRequired,
        needsAuthentication = false,
        shouldLock = false,
        screenPresentationStyle = ScreenPresentationStyle.Modal,
        eventTrackerContext = NfcEventTrackerScreenIdContext.WIPE_DEVICE_CLASSIFY_DEVICE,
        showNativeSheetOnIos = false,
        skipFirmwareTelemetry = true
      )
    )
  }

  private suspend fun NfcCommands.classifyTappedDevice(
    session: NfcSession,
    account: FullAccount,
  ): TappedDevice {
    val deviceInfo = getDeviceInfo(session)
    val hardwareType = deviceInfo.hardwareType()
    val authKey = if (account.config.hardwareType == hardwareType) {
      getAuthenticationKey(session)
    } else {
      null
    }
    val initialSpendingKeyFingerprint = readInitialSpendingKeyFingerprintOrNull(session, account)

    return TappedDevice(
      deviceInfo = deviceInfo,
      hardwareType = hardwareType,
      authKey = authKey,
      initialSpendingKeyFingerprint = initialSpendingKeyFingerprint
    )
  }

  private suspend fun NfcCommands.readInitialSpendingKeyFingerprintOrNull(
    session: NfcSession,
    account: FullAccount,
  ): String? {
    return try {
      getInitialSpendingKey(session, account.config.bitcoinNetworkType)
        .key
        .origin
        .fingerprint
    } catch (e: NfcException.CommandErrorUnauthenticated) {
      handleUnauthenticatedInitialSpendingKey(session, e)
    } catch (e: NfcException) {
      logWarn(throwable = e) {
        "Unable to read initial spending key while classifying tapped device for wipe"
      }
      null
    }
  }

  private suspend fun NfcCommands.handleUnauthenticatedInitialSpendingKey(
    session: NfcSession,
    initialSpendingKeyError: NfcException.CommandErrorUnauthenticated,
  ): String? {
    val enrolledFingerprints = try {
      getEnrolledFingerprints(session)
    } catch (e: NfcException.CommandErrorUnauthenticated) {
      throw e
    } catch (e: NfcException) {
      logWarn(throwable = e) {
        "Unable to read enrolled fingerprints while classifying tapped device for wipe"
      }
      return null
    }

    if (enrolledFingerprints.fingerprintHandles.isEmpty()) {
      throw NfcException.DeviceAlreadyWipedOrNotSetUp()
    }

    logWarn(throwable = initialSpendingKeyError) {
      "Unable to read initial spending key while classifying tapped device for wipe"
    }
    return null
  }

  @Composable
  private fun UnpairedDeviceTapModel(
    onTapDevice: () -> Unit,
    onCancel: () -> Unit,
  ): ScreenModel {
    return nfcSessionUIStateMachine.model(
      NfcSessionUIStateMachineProps(
        session = { session, commands ->
          commands.getDeviceInfo(session)
        },
        onSuccess = { onTapDevice() },
        onCancel = onCancel,
        hardwareVerification = NotRequired,
        needsAuthentication = false,
        shouldLock = false,
        screenPresentationStyle = ScreenPresentationStyle.Modal,
        eventTrackerContext = NfcEventTrackerScreenIdContext.HW_PROOF_OF_POSSESSION
      )
    )
  }

  @Composable
  private fun UnpairedDeviceWarningSheet(
    onWipeDevice: () -> Unit,
    onCancel: () -> Unit,
  ): SheetModel {
    return SheetModel(
      size = SheetSize.DEFAULT,
      onClosed = onCancel,
      body = UnpairedDeviceWarningSheetBodyModel(
        subline = "This device might be protecting funds. If you wipe the device, the funds may no longer be accessible.",
        onWipeDevice = onWipeDevice,
        onCancel = onCancel
      )
    )
  }

  @Composable
  private fun ActiveDeviceInfoModel(
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    bottomSheet: SheetModel? = null,
  ): ScreenModel {
    return ScreenModel(
      body = ActiveDeviceInfoBodyModel(
        onContinue = onContinue,
        onCancel = onCancel
      ),
      bottomSheetModel = bottomSheet,
      presentationStyle = ScreenPresentationStyle.Modal
    )
  }

  private data class ActiveDeviceInfoBodyModel(
    val onContinue: () -> Unit,
    val onCancel: () -> Unit,
  ) : FormBodyModel(
      id = WipingDeviceEventTrackerScreenId.RESET_DEVICE_INTRO,
      onBack = onCancel,
      toolbar = ToolbarModel(
        leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(onCancel)
      ),
      header = FormHeaderModel(
        headline = "Permanently wipe your current Bitkey device",
        subline = "We noticed you tapped the Bitkey device that is currently paired to your wallet.\n\n" +
          "If you want to wipe a Bitkey that was previously paired to your wallet, go back and try again using your other Bitkey device."
      ),
      primaryButton = ButtonModel(
        text = "Continue",
        onClick = StandardClick(onContinue),
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.Primary
      ),
      secondaryButton = ButtonModel(
        text = "Cancel",
        onClick = StandardClick(onCancel),
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.Secondary
      )
    )

  @Composable
  private fun OldDeviceBlockingModel(
    id: WipingDeviceEventTrackerScreenId,
    headline: String,
    subline: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
  ): ScreenModel {
    return ScreenModel(
      body = OldDeviceBlockingBodyModel(
        eventTrackerScreenId = id,
        headline = headline,
        subline = subline,
        onRetry = onRetry,
        onCancel = onCancel
      ),
      presentationStyle = ScreenPresentationStyle.Modal
    )
  }

  private fun AlreadyWipedOrNotSetUpModel(onDone: () -> Unit): ScreenModel {
    return ErrorFormBodyModel(
      title = "No wipe needed",
      subline = "This Bitkey is already wiped or hasn’t been set up.",
      primaryButton = ButtonDataModel("Done", onClick = onDone),
      onBack = onDone,
      eventTrackerScreenId =
        WipingDeviceEventTrackerScreenId.RESET_DEVICE_OLD_DEVICE_ALREADY_WIPED_OR_NOT_SET_UP
    ).asScreen(ScreenPresentationStyle.Modal)
  }

  private data class OldDeviceBlockingBodyModel(
    val eventTrackerScreenId: WipingDeviceEventTrackerScreenId,
    val headline: String,
    val subline: String,
    val onRetry: () -> Unit,
    val onCancel: () -> Unit,
  ) : FormBodyModel(
      id = eventTrackerScreenId,
      onBack = onCancel,
      toolbar = ToolbarModel(
        leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(onCancel)
      ),
      header = FormHeaderModel(
        icon = Icon.LargeIconWarningFilled,
        headline = headline,
        subline = subline
      ),
      primaryButton = ButtonModel(
        text = "Try again",
        onClick = StandardClick(onRetry),
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.Primary
      ),
      secondaryButton = ButtonModel(
        text = "Cancel",
        onClick = StandardClick(onCancel),
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.Secondary
      )
    )

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
      val convertedFiatBalance = currencyConverter
        .convert(
          fromAmount = balance.total,
          toCurrency = fiatCurrency,
          atTime = null
        ).filterNotNull().firstOrNull() as? FiatMoney
        ?: FiatMoney.zero(fiatCurrency)

      fiatBalance = convertedFiatBalance

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
   * Warning state for unpaired device
   */
  data object UnpairedDeviceWarningState : WipingDeviceIntroUiState

  data class ActiveDeviceInfoState(
    val eligibility: ActiveDeviceEligibility,
    val isShowingTransferFundsSheet: Boolean = false,
  ) : WipingDeviceIntroUiState

  data object ActiveDeviceTappedForOldWipeState : WipingDeviceIntroUiState

  data class CheckingLoggedInDeviceState(
    val account: FullAccount,
    val tappedDevice: TappedDeviceIdentity,
  ) : WipingDeviceIntroUiState

  data object OldDeviceNotReadyToWipeState : WipingDeviceIntroUiState

  data object OldDeviceHasFundsState : WipingDeviceIntroUiState

  data object UnknownOldDeviceState : WipingDeviceIntroUiState

  data object OldDeviceCheckFailedState : WipingDeviceIntroUiState

  data object AlreadyWipedOrNotSetUpState : WipingDeviceIntroUiState
}

private sealed interface ActiveDeviceEligibility {
  data object Ready : ActiveDeviceEligibility

  data class HasFunds(
    val balance: BitcoinBalance,
  ) : ActiveDeviceEligibility
}

private data class TappedDevice(
  val deviceInfo: FirmwareDeviceInfo,
  val hardwareType: HardwareType,
  val authKey: HwAuthPublicKey?,
  val initialSpendingKeyFingerprint: String?,
)

private fun TappedDevice.identity(): TappedDeviceIdentity =
  TappedDeviceIdentity(
    deviceInfo = deviceInfo,
    authKey = authKey,
    initialSpendingKeyFingerprint = initialSpendingKeyFingerprint
  )
