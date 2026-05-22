package build.wallet.statemachine.settings.full.device.wipedevice.confirmation

import androidx.compose.runtime.*
import bitkey.account.AccountConfigService
import bitkey.account.DefaultAccountConfig
import bitkey.account.FullAccountConfig
import bitkey.account.HardwareType
import bitkey.firmware.HardwareUnlockInfoService
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.bitkey.account.FullAccount
import build.wallet.device.wipe.DeviceWipeEligibilityService
import build.wallet.device.wipe.InactiveDeviceWipeValidationError
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.logging.logDebug
import build.wallet.logging.logWarn
import build.wallet.nfc.NfcException
import build.wallet.statemachine.core.*
import build.wallet.statemachine.nfc.ConfirmationHandlerOverride
import build.wallet.statemachine.nfc.ConfirmationResultContent
import build.wallet.statemachine.nfc.NfcConfirmableSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcConfirmableSessionUiStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationContent
import build.wallet.statemachine.settings.full.device.wipedevice.WipeContext
import build.wallet.statemachine.settings.full.device.wipedevice.confirmation.WipingDeviceConfirmationUiState.ConfirmationScreen
import build.wallet.statemachine.settings.full.device.wipedevice.confirmation.WipingDeviceConfirmationUiState.WipingDevice
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.firstOrNull

@BitkeyInject(ActivityScope::class)
class WipingDeviceConfirmationUiStateMachineImpl(
  private val nfcConfirmableSessionUiStateMachine: NfcConfirmableSessionUiStateMachine,
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
  private val hardwareUnlockInfoService: HardwareUnlockInfoService,
  private val accountConfigService: AccountConfigService,
  private val deviceWipeEligibilityService: DeviceWipeEligibilityService,
) : WipingDeviceConfirmationUiStateMachine {
  private val wipeConfirmationMessages = arrayOf(
    "Wiping disconnects this device from your Bitkey wallet.",
    "This device will no longer access the funds in your wallet.",
    "This device will no longer help recover your wallet.",
    "After the wipe is complete, you can safely give away or dispose of this device."
  )

  @Composable
  override fun model(props: WipingDeviceConfirmationProps): ScreenModel {
    val confirmationMessages = wipeConfirmationMessages
    var uiState: WipingDeviceConfirmationUiState by remember(props.wipeContext) {
      mutableStateOf(ConfirmationScreen())
    }
    // List to manage the states of the checkboxes
    var confirmationMessageStates by remember(props.wipeContext) {
      mutableStateOf<ImmutableList<WipingDeviceConfirmationState>>(
        List(confirmationMessages.size) {
          WipingDeviceConfirmationState.NotCompleted
        }.toImmutableList()
      )
    }

    when (val state = uiState) {
      is ConfirmationScreen -> {
        val allMessagesChecked = confirmationMessageStates.all {
          it is WipingDeviceConfirmationState.Completed
        }

        val onConfirmWipeDevice: () -> Unit = {
          if (allMessagesChecked) {
            uiState = state.copy(
              isShowingScanAndWipeSheet = true
            )
          }
        }

        return WipingDeviceConfirmationModel(
          onBack = props.onBack,
          onConfirmWipeDevice = onConfirmWipeDevice,
          messageItemModels = confirmationMessages.mapIndexed { index, message ->
            WipingDeviceConfirmationItemModel(
              state = confirmationMessageStates[index],
              title = message,
              onClick = {
                confirmationMessageStates = confirmationMessageStates.toMutableList().apply {
                  this[index] = when (this[index]) {
                    is WipingDeviceConfirmationState.Completed -> WipingDeviceConfirmationState.NotCompleted
                    is WipingDeviceConfirmationState.NotCompleted -> WipingDeviceConfirmationState.Completed
                  }
                }.toImmutableList()
              }
            )
          }.toImmutableList(),
          isConfirmEnabled = allMessagesChecked,
          bottomSheetModel =
            if (state.isShowingScanAndWipeSheet) {
              ScanAndWipeConfirmationSheet(
                onBack = { uiState = state.copy(isShowingScanAndWipeSheet = false) },
                onConfirmWipeDevice = {
                  uiState = WipingDevice
                }
              )
            } else {
              null
            }
        )
      }

      is WipingDevice -> {
        return WipeDeviceModel(
          fullAccount = props.fullAccount,
          onSuccess = props.onWipeDevice,
          onCancel = {
            uiState = ConfirmationScreen()
          },
          isDevicePaired = props.isDevicePaired,
          wipeContext = props.wipeContext
        )
      }
    }
  }

  @Composable
  private fun WipingDeviceConfirmationModel(
    onBack: () -> Unit,
    onConfirmWipeDevice: () -> Unit,
    messageItemModels: ImmutableList<WipingDeviceConfirmationItemModel>,
    isConfirmEnabled: Boolean,
    bottomSheetModel: SheetModel? = null,
  ): ScreenModel {
    return ScreenModel(
      body = WipingDeviceConfirmationBodyModel(
        onBack = onBack,
        onConfirmWipeDevice = onConfirmWipeDevice,
        messageItemModels = messageItemModels,
        isConfirmEnabled = isConfirmEnabled
      ),
      bottomSheetModel = bottomSheetModel
    )
  }

  @Composable
  private fun ScanAndWipeConfirmationSheet(
    onBack: () -> Unit,
    onConfirmWipeDevice: () -> Unit,
  ): SheetModel {
    return SheetModel(
      size = SheetSize.DEFAULT,
      onClosed = onBack,
      body = ScanAndWipeConfirmationSheetBodyModel(
        onBack = onBack,
        onConfirmWipeDevice = onConfirmWipeDevice
      )
    )
  }

  @Composable
  private fun WipeDeviceModel(
    fullAccount: FullAccount?,
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    isDevicePaired: Boolean,
    wipeContext: WipeContext = WipeContext.Default,
  ): ScreenModel {
    val defaultConfig by remember {
      accountConfigService.activeOrDefaultConfig()
    }.collectAsState()
    val hardwareType = fullAccount?.config?.hardwareType
      ?: when (val config = defaultConfig) {
        is FullAccountConfig -> config.hardwareType
        // Fail-safe to W3 behavior when hardware type is unknown.
        is DefaultAccountConfig -> config.hardwareType ?: HardwareType.W3
        else -> HardwareType.W3
      }
    val bitcoinNetworkType = fullAccount?.config?.bitcoinNetworkType
      ?: defaultConfig.bitcoinNetworkType

    val (hardwareTypeOverride, hardwareVerification, needsAuth) =
      resolveWipeConfig(wipeContext, isDevicePaired)
    val isW3 = (hardwareTypeOverride ?: hardwareType) == HardwareType.W3
    return nfcConfirmableSessionUiStateMachine.model(
      NfcConfirmableSessionUIStateMachineProps(
        session = { session, commands ->
          if (wipeContext is WipeContext.InactiveDevice) {
            deviceWipeEligibilityService.validateInactiveDeviceForWipe(
              account = fullAccount,
              session = session,
              commands = commands,
              expectedDevice = wipeContext.device,
              bitcoinNetworkType = bitcoinNetworkType
            ).fold(
              success = {},
              failure = { throw it.toNfcException() }
            )
          }
          commands.wipeDevice(session)
        },
        onSuccess = { success: Boolean ->
          if (success) {
            val firmwareSerial = firmwareDeviceInfoDao.deviceInfo().firstOrNull()?.get()?.serial
              ?: "failed to retrieve serial number"
            logDebug { "Bitkey wipe successfully with serial number: $firmwareSerial" }
            if (isDevicePaired) {
              firmwareDeviceInfoDao.clear()
              hardwareUnlockInfoService.clear()
            }
            recordW3UpgradeOldW1WipedIfApplicable(
              fullAccount = fullAccount,
              wipeContext = wipeContext
            )
            onSuccess()
          } else {
            onCancel()
          }
        },
        onCancel = onCancel,
        needsAuthentication = needsAuth,
        hardwareVerification = hardwareVerification,
        screenPresentationStyle = ScreenPresentationStyle.Modal,
        shouldLock = false,
        eventTrackerContext = NfcEventTrackerScreenIdContext.WIPE_DEVICE,
        confirmationContent = HardwareConfirmationContent.WipeDevice,
        confirmationResultContent = ConfirmationResultContent(
          pendingHeadline = "Confirm the wipe on your Bitkey",
          pendingSubline = "You'll need to approve or deny the wipe on your Bitkey device before tapping again."
        ),
        showNativeSheetOnIos = !isW3,
        // W1: Skip second tap (legacy behavior - firmware wipes immediately).
        // W3: Use full two-tap flow (firmware requires on-device confirmation).
        hardwareTypeOverride = hardwareTypeOverride,
        skipFirmwareTelemetry = wipeContext is WipeContext.InactiveDevice,
        onRequiresConfirmation = if (isW3) {
          null // Use default two-tap behavior
        } else {
          { _ -> ConfirmationHandlerOverride.CompleteImmediately(true) }
        },
        onEmulatedPromptSelected = if (isW3) {
          null // Use default emulated prompt behavior (show confirmation screen)
        } else {
          { isApprove, _ ->
            // W1 fake hardware: skip the second NFC tap and complete immediately
            ConfirmationHandlerOverride.CompleteImmediately(isApprove)
          }
        }
      )
    )
  }

  private suspend fun recordW3UpgradeOldW1WipedIfApplicable(
    fullAccount: FullAccount?,
    wipeContext: WipeContext,
  ) {
    val account = fullAccount ?: return
    val device = (wipeContext as? WipeContext.InactiveDevice)?.device ?: return

    deviceWipeEligibilityService
      .recordW3UpgradeOldW1WipedIfApplicable(account, device)
      .onFailure { error ->
        logWarn(throwable = error) { "Failed to record old-W1 wipe completion" }
      }
  }
}

private fun InactiveDeviceWipeValidationError.toNfcException(): NfcException =
  when (this) {
    InactiveDeviceWipeValidationError.DeviceLocked ->
      NfcException.CommandErrorUnauthenticated()
    InactiveDeviceWipeValidationError.FeatureDisabled ->
      NfcException.CommandError("Inactive device wipe is disabled")
    InactiveDeviceWipeValidationError.WrongDevice ->
      NfcException.CommandError("Wrong inactive device tapped")
    InactiveDeviceWipeValidationError.MissingBitcoinNetworkType ->
      NfcException.CommandError("Unable to verify inactive device without account network")
    InactiveDeviceWipeValidationError.DeviceCheckFailed ->
      NfcException.CommandError("Unable to verify inactive device")
    InactiveDeviceWipeValidationError.OldDeviceSweepPendingConfirmation ->
      NfcException.CommandError("Inactive device transfer still confirming")
  }

private data class WipeDeviceConfig(
  val hardwareTypeOverride: HardwareType?,
  val hardwareVerification: HardwareVerification,
  val needsAuth: Boolean,
)

private fun resolveWipeConfig(
  wipeContext: WipeContext,
  isDevicePaired: Boolean,
): WipeDeviceConfig =
  when (wipeContext) {
    is WipeContext.InactiveDevice -> WipeDeviceConfig(
      hardwareTypeOverride = wipeContext.device.hardwareType,
      hardwareVerification = HardwareVerification.NotRequired,
      needsAuth = true
    )
    is WipeContext.Default -> WipeDeviceConfig(
      hardwareTypeOverride = null,
      hardwareVerification = if (isDevicePaired) {
        HardwareVerification.Required()
      } else {
        HardwareVerification.NotRequired
      },
      needsAuth = true
    )
  }

private sealed interface WipingDeviceConfirmationUiState {
  /**
   * Viewing the wipe device intro screen
   */
  data class ConfirmationScreen(
    val isShowingScanAndWipeSheet: Boolean = false,
  ) : WipingDeviceConfirmationUiState

  /**
   * Wiping the device
   */
  data object WipingDevice : WipingDeviceConfirmationUiState
}
