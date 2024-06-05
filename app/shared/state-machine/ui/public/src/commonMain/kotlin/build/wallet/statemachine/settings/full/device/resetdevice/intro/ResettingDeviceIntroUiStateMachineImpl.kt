package build.wallet.statemachine.settings.full.device.resetdevice.intro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.encrypt.SignatureVerifier
import build.wallet.encrypt.verifyEcdsaResult
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.SheetSize
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.settings.full.device.resetdevice.ResettingDeviceEventTrackerScreenId
import build.wallet.statemachine.settings.full.device.resetdevice.intro.ResettingDeviceIntroUiState.IntroState
import build.wallet.statemachine.settings.full.device.resetdevice.intro.ResettingDeviceIntroUiState.ScanToContinueState
import build.wallet.statemachine.settings.full.device.resetdevice.intro.ResettingDeviceIntroUiState.ScanningState
import build.wallet.statemachine.settings.full.device.resetdevice.intro.ResettingDeviceIntroUiState.TransferringFundsState
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemTitleAlignment
import build.wallet.ui.model.list.ListItemTreatment
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
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
          onBack = props.onBack,
          onResetDevice = { uiState = ScanToContinueState },
          bottomSheet = bottomSheet
        )
      }

      is ScanningState -> {
        initialDeviceTapModel(
          pubKey = props.keybox.activeHwKeyBundle.authKey.pubKey,
          balance = props.balance,
          isHardwareFake = props.isHardwareFake,
          onTapPairedDevice = { balance ->
            if (balance != null) {
              scope.launch {
                props.spendingWallet.isBalanceSpendable()
                  .onSuccess { isSpendable ->
                    if (isSpendable) {
                      uiState = TransferringFundsState(balance)
                    } else {
                      props.onDeviceConfirmed()
                    }
                  }.onFailure {
                    // TODO: Show error screen
                    TODO()
                  }
              }
            } else {
              props.onDeviceConfirmed()
            }
          },
          onTapUnknownDevice = {
            // TODO: Show error screen
            uiState = IntroState()
          },
          onCancel = {
            uiState = IntroState()
          }
        )
      }

      is TransferringFundsState -> {
        ResettingDeviceIntroModel(
          onBack = props.onBack,
          onResetDevice = { uiState = ScanToContinueState },
          bottomSheet = TransferFundsBeforeResetSheet(
            onTransferFunds = {
              uiState = IntroState(shouldUnwindToMoneyHome = true)
            },
            onCancel = { uiState = IntroState() },
            balance = state.balance!!
          )
        )
      }
    }
  }

  @Composable
  fun ResettingDeviceIntroModel(
    onBack: () -> Unit,
    onResetDevice: () -> Unit,
    bottomSheet: SheetModel? = null,
  ): ScreenModel {
    val resettingDeviceModel = FormBodyModel(
      id = ResettingDeviceEventTrackerScreenId.RESET_DEVICE_INTRO,
      onBack = null,
      toolbar = ToolbarModel(
        leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(onBack)
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
  private fun initialDeviceTapModel(
    pubKey: Secp256k1PublicKey,
    balance: BitcoinBalance,
    isHardwareFake: Boolean,
    onTapPairedDevice: (spendableBalance: BitcoinBalance?) -> Unit,
    onTapUnknownDevice: () -> Unit,
    onCancel: () -> Unit,
  ): ScreenModel {
    val challengeString = "verify-paired-device".encodeUtf8()

    return nfcSessionUIStateMachine.model(
      NfcSessionUIStateMachineProps(
        session = { session, commands ->
          commands.signChallenge(session, challengeString)
        },
        onSuccess = { signature ->
          val verification = signatureVerifier.verifyEcdsaResult(
            message = challengeString,
            signature = signature,
            publicKey = pubKey
          )
          if (verification.get() == true) {
            val isSpendable = balance.spendable.value.isPositive
            val balance = if (isSpendable) balance else null
            onTapPairedDevice(balance)
          } else {
            onTapUnknownDevice()
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
    onTransferFunds: () -> Unit,
    onCancel: () -> Unit,
    balance: BitcoinBalance,
  ): SheetModel {
    var fiatBalance: FiatMoney? by remember { mutableStateOf(null) }
    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()

    SyncFiatBalanceEquivalentEffect(balance, fiatCurrency) {
      fiatBalance = it
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
          // TODO: Get mobile limit value
          subline = "Once reset, you won’t be able to transfer funds above your mobile limit."
        ),
        mainContentList = immutableListOf(
          FormMainContentModel.ListGroup(
            listGroupModel = ListGroupModel(
              header = "Your funds",
              headerTreatment = ListGroupModel.HeaderTreatment.PRIMARY,
              items = immutableListOf(
                ListItemModel(
                  title = when (val fiatBalance = fiatBalance) {
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
    )
  }

  @Composable
  private fun SyncFiatBalanceEquivalentEffect(
    balance: BitcoinBalance,
    fiatCurrency: FiatCurrency,
    onFiatBalanceSynced: (FiatMoney) -> Unit,
  ) {
    // update the fiat balance if balance has changed or the selected fiat currency changes
    LaunchedEffect(
      "sync-fiat-equivalent-balance",
      balance.total,
      fiatCurrency
    ) {
      val convertedFiatBalance =
        currencyConverter
          .convert(
            fromAmount = balance.total,
            toCurrency = fiatCurrency,
            atTime = null
          ).filterNotNull().firstOrNull() as? FiatMoney
          ?: FiatMoney.zero(fiatCurrency)

      onFiatBalanceSynced(convertedFiatBalance)
    }
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
   * Viewing the transfer funds before reset bottom sheet
   */
  data class TransferringFundsState(val balance: BitcoinBalance) : ResettingDeviceIntroUiState
}
