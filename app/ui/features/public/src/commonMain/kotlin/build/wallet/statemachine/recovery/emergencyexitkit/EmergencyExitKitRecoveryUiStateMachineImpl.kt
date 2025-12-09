package build.wallet.statemachine.recovery.emergencyexitkit

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.EmergencyAccessKitTrackerScreenId
import build.wallet.cloud.backup.csek.Csek
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.emergencyexitkit.EmergencyExitKitPayload
import build.wallet.emergencyexitkit.EmergencyExitKitPayloadDecoder
import build.wallet.emergencyexitkit.EmergencyExitPayloadRestorer
import build.wallet.keybox.KeyboxDao
import build.wallet.nfc.platform.unsealSymmetricKey
import build.wallet.platform.clipboard.Clipboard
import build.wallet.platform.permissions.Permission
import build.wallet.platform.random.UuidGenerator
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
import build.wallet.statemachine.platform.permissions.PermissionUiProps
import build.wallet.statemachine.platform.permissions.PermissionUiStateMachine
import build.wallet.statemachine.recovery.emergencyexitkit.EmergencyExitKitRecoveryUiStateMachineImpl.State.*
import build.wallet.statemachine.recovery.emergencyexitkit.EmergencyExitKitRecoveryUiStateMachineImpl.State.EntrySource.ManualEntry
import build.wallet.statemachine.recovery.emergencyexitkit.EmergencyExitKitRecoveryUiStateMachineImpl.State.EntrySource.QrEntry
import build.wallet.statemachine.send.QrCodeScanBodyModel
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@BitkeyInject(ActivityScope::class)
class EmergencyExitKitRecoveryUiStateMachineImpl(
  private val clipboard: Clipboard,
  private val payloadDecoder: EmergencyExitKitPayloadDecoder,
  private val permissionUiStateMachine: PermissionUiStateMachine,
  private val emergencyExitPayloadRestorer: EmergencyExitPayloadRestorer,
  private val csekDao: CsekDao,
  private val keyboxDao: KeyboxDao,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val uuidGenerator: UuidGenerator,
) : EmergencyExitKitRecoveryUiStateMachine {
  @Composable
  override fun model(props: EmergencyExitKitRecoveryUiStateMachineProps): ScreenModel {
    var state: State by remember {
      mutableStateOf(
        SelectInputMethod
      )
    }

    return when (val currentState = state) {
      is SelectInputMethod -> {
        val onBack = remember { { state = currentState.onBack(props) } }
        val onEnterManually =
          remember(currentState) { { state = currentState.onSelectManualEntry() } }
        val onScanQrCode = remember(currentState) {
          {
            state = when {
              permissionUiStateMachine.isImplemented -> RequestingCameraPermission
              else -> State.QrEntry
            }
          }
        }
        EmergencyExitKitImportWalletBodyModel(
          onBack = onBack,
          onEnterManually = onEnterManually,
          onScanQRCode = onScanQrCode
        ).asRootScreen()
      }

      is RequestingCameraPermission ->
        permissionUiStateMachine.model(
          PermissionUiProps(
            permission = Permission.Camera,
            onExit = { currentState.onBack(props) },
            onGranted = { state = State.QrEntry }
          )
        ).asRootScreen()

      is State.ManualEntry -> {
        var payloadToProcess: String? by remember { mutableStateOf(null) }
        payloadToProcess?.let { payload ->
          LaunchedEffect("process-payload", payload) {
            payloadDecoder.decode(payload)
              .onSuccess {
                state = RestoreWallet(
                  payload = it,
                  entrySource = ManualEntry(payload)
                )
              }
              .onFailure {
                state = CodeNotRecognized(entrySource = ManualEntry(payload))
              }
            payloadToProcess = null
          }
        }
        EmergencyExitKitImportPasteAppKeyBodyModel(
          enteredText = currentState.enteredText,
          onEnterTextChanged = { newText ->
            state = currentState.onManualEntryTextChanged(newText)
          },
          onBack = { state = currentState.onBack(props) },
          onContinue = {
            payloadToProcess = currentState.enteredText
          },
          onPasteButtonClick = {
            clipboard.getPlainTextItem()?.let {
              state = currentState.onManualEntryTextChanged(it.data)
            }
          }
        ).asRootScreen()
      }

      is State.QrEntry -> {
        var qrCodeDataToProcess: String? by remember { mutableStateOf(null) }
        qrCodeDataToProcess?.let { qrCodeData ->
          LaunchedEffect("process-qr-code-data", qrCodeData) {
            payloadDecoder.decode(qrCodeData)
              .onSuccess {
                state = RestoreWallet(payload = it, entrySource = QrEntry)
              }
              .onFailure {
                state = CodeNotRecognized(entrySource = QrEntry)
              }
            qrCodeDataToProcess = null
          }
        }
        QrCodeScanBodyModel(
          headline = "Scan your Emergency Exit Kit",
          reticleLabel = "Scan the QR code in section 4 of the PDF",
          onClose = { state = currentState.onBack(props) },
          onQrCodeScanned = { rawData ->
            qrCodeDataToProcess = rawData
          },
          eventTrackerScreenInfo =
            EventTrackerScreenInfo(
              eventTrackerScreenId = EmergencyAccessKitTrackerScreenId.SCAN_QR_CODE
            )
        ).asFullScreen()
      }

      is CodeNotRecognized -> {
        EmergencyExitKitCodeNotRecognizedBodyModel(
          arrivedFromManualEntry = currentState.arrivedFromManualEntry(),
          onBack = { state = currentState.onBack(props) },
          onScanQRCode = { state = currentState.onScanQrCode() },
          onImport = { state = currentState.onImport() }
        ).asRootScreen()
      }

      is RestoreWallet -> {
        val onRestore: (() -> Unit)? = remember(currentState) {
          { state = currentState.onStartRestore() }
        }
        EmergencyExitKitRestoreWalletBodyModel(
          onBack = { state = currentState.onBack(props) },
          onRestore = onRestore
        ).asRootScreen()
      }

      is StartNFCRestore -> {
        val sealedCsek =
          when (currentState.payload) {
            is EmergencyExitKitPayload.EmergencyExitKitPayloadV1 ->
              currentState.payload.sealedHwEncryptionKey
          }
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              Csek(commands.unsealSymmetricKey(session, sealedCsek))
            },
            onSuccess = { unsealedCsek ->
              csekDao.set(
                key = sealedCsek,
                value = unsealedCsek
              )

              state = currentState.onSuccess()
            },
            onCancel = { state = currentState.onBack(props) },
            screenPresentationStyle = ScreenPresentationStyle.Root,
            eventTrackerContext = NfcEventTrackerScreenIdContext.UNSEAL_EMERGENCY_ACCESS_KIT_BACKUP,
            hardwareVerification = NotRequired // EEK recovery happens without an active account
          )
        )
      }

      is RestoreCompleting -> {
        LaunchedEffect("restoring-from-backup") {
          emergencyExitPayloadRestorer.restoreFromPayload(currentState.payload)
            .onSuccess {
              state = RestoreCompleted(it)
            }
            .onFailure {
              state = RestoreFailed
            }
        }

        importingBackupScreen().asRootScreen()
      }

      is RestoreCompleted -> {
        LaunchedEffect("applying-backup") {
          currentState.completeRestore(keyboxDao = keyboxDao, uuidGenerator = uuidGenerator)
        }
        importingBackupScreen().asRootScreen()
      }

      is RestoreFailed ->
        EmergencyExitKitCodeNotRecognizedBodyModel(
          arrivedFromManualEntry = false,
          onBack = { state = currentState.onBack(props) },
          onScanQRCode = { state = currentState.onScanQRCode() },
          onImport = { state = currentState.onImport() }
        ).asRootScreen()
    }
  }

  private fun importingBackupScreen(): LoadingSuccessBodyModel =
    LoadingBodyModel(
      onBack = null,
      title = "Importing Emergency Exit Kit backup...",
      id = EmergencyAccessKitTrackerScreenId.LOADING_BACKUP
    )

  private sealed interface State {
    /** Initial screen with the option to select QR code scanning or import/manual entry */
    data object SelectInputMethod : State {
      fun onSelectManualEntry() = ManualEntry(enteredText = "")
    }

    /** Requesting camera permission to scan the QR code */
    data object RequestingCameraPermission : State

    /** Showing the QR scanning screen to load the Emergency Exit Kit payload */
    data object QrEntry : State

    /** Importing via pasting or manually typing in the Emergency Exit Kit payload */
    data class ManualEntry(val enteredText: String) : State {
      fun onManualEntryTextChanged(newText: String): State {
        return if (this.enteredText != newText) {
          this.copy(enteredText = newText)
        } else {
          this
        }
      }
    }

    /**
     * The entered Emergency Exit Kit payload was not recognized, due to a parsing error
     * or invalid contents
     * */
    data class CodeNotRecognized(val entrySource: EntrySource) : State {
      fun onScanQrCode(): State = QrEntry

      fun onImport(): State {
        val enteredText =
          when (this.entrySource) {
            EntrySource.QrEntry -> ""
            is EntrySource.ManualEntry -> this.entrySource.enteredText
          }
        return ManualEntry(enteredText = enteredText)
      }

      fun arrivedFromManualEntry(): Boolean {
        return when (this.entrySource) {
          EntrySource.QrEntry -> false
          is EntrySource.ManualEntry -> true
        }
      }
    }

    sealed interface EntrySource {
      data object QrEntry : EntrySource

      data class ManualEntry(val enteredText: String) : EntrySource
    }

    /**
     * Screen providing the instructions to restore the wallet from the Emergency Exit Kit
     * kit payload.
     * */
    data class RestoreWallet(
      val payload: EmergencyExitKitPayload,
      val entrySource: EntrySource,
    ) : State {
      fun onStartRestore(): State =
        StartNFCRestore(
          payload = this.payload,
          entrySource = this.entrySource
        )
    }

    /**
     * The sub flow for communicating with the bitkey via NFC to decrypt the emergency
     * access payload, and restore the wallet.
     */
    data class StartNFCRestore(
      val payload: EmergencyExitKitPayload,
      val entrySource: EntrySource,
    ) : State {
      fun onSuccess(): State = RestoreCompleting(payload = payload)
    }

    data class RestoreCompleting(
      val payload: EmergencyExitKitPayload,
    ) : State

    data object RestoreFailed : State {
      fun onScanQRCode() = QrEntry

      fun onImport() = ManualEntry(enteredText = "")
    }

    data class RestoreCompleted(
      val accountRestoration: EmergencyExitPayloadRestorer.AccountRestoration,
    ) : State {
      suspend fun completeRestore(
        keyboxDao: KeyboxDao,
        uuidGenerator: UuidGenerator,
      ) = coroutineBinding {
        // Only set the active keybox. This will leave the app in a "server offline" state
        // but able to transfer funds.
        val activeKeybox = accountRestoration.asKeybox(
          keyboxId = uuidGenerator.random(),
          appKeyBundleId = uuidGenerator.random(),
          hwKeyBundleId = uuidGenerator.random()
        )

        keyboxDao
          .saveKeyboxAsActive(activeKeybox)
          .bind()
      }
    }

    fun onBack(props: EmergencyExitKitRecoveryUiStateMachineProps): State {
      return when (this) {
        SelectInputMethod -> {
          props.onExit()
          SelectInputMethod
        }
        is RequestingCameraPermission -> SelectInputMethod
        is QrEntry -> SelectInputMethod
        is ManualEntry -> SelectInputMethod
        is CodeNotRecognized -> {
          when (this.entrySource) {
            EntrySource.QrEntry -> QrEntry
            is EntrySource.ManualEntry -> ManualEntry(enteredText = this.entrySource.enteredText)
          }
        }
        is RestoreWallet -> {
          when (this.entrySource) {
            EntrySource.QrEntry -> QrEntry
            is EntrySource.ManualEntry -> ManualEntry(enteredText = this.entrySource.enteredText)
          }
        }
        is StartNFCRestore ->
          RestoreWallet(payload = this.payload, entrySource = this.entrySource)
        is RestoreCompleting -> SelectInputMethod
        is RestoreCompleted -> SelectInputMethod
        RestoreFailed ->
          SelectInputMethod
      }
    }
  }
}
