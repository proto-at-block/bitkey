package build.wallet.statemachine.recovery.emergencyaccesskit

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.EmergencyAccessKitTrackerScreenId
import build.wallet.cloud.backup.csek.Csek
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.emergencyaccesskit.EmergencyAccessKitPayload
import build.wallet.emergencyaccesskit.EmergencyAccessKitPayloadDecoder
import build.wallet.emergencyaccesskit.EmergencyAccessPayloadRestorer
import build.wallet.keybox.KeyboxDao
import build.wallet.platform.clipboard.Clipboard
import build.wallet.platform.permissions.Permission
import build.wallet.platform.random.UuidGenerator
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.platform.permissions.PermissionUiProps
import build.wallet.statemachine.platform.permissions.PermissionUiStateMachine
import build.wallet.statemachine.recovery.emergencyaccesskit.EmergencyAccessKitRecoveryUiStateMachineImpl.State.*
import build.wallet.statemachine.recovery.emergencyaccesskit.EmergencyAccessKitRecoveryUiStateMachineImpl.State.EntrySource.ManualEntry
import build.wallet.statemachine.recovery.emergencyaccesskit.EmergencyAccessKitRecoveryUiStateMachineImpl.State.EntrySource.QrEntry
import build.wallet.statemachine.send.QrCodeScanBodyModel
import build.wallet.toByteString
import build.wallet.toUByteList
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@BitkeyInject(ActivityScope::class)
class EmergencyAccessKitRecoveryUiStateMachineImpl(
  private val clipboard: Clipboard,
  private val payloadDecoder: EmergencyAccessKitPayloadDecoder,
  private val permissionUiStateMachine: PermissionUiStateMachine,
  private val emergencyAccessPayloadRestorer: EmergencyAccessPayloadRestorer,
  private val csekDao: CsekDao,
  private val keyboxDao: KeyboxDao,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val uuidGenerator: UuidGenerator,
) : EmergencyAccessKitRecoveryUiStateMachine {
  @Composable
  override fun model(props: EmergencyAccessKitRecoveryUiStateMachineProps): ScreenModel {
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
        EmergencyAccessKitImportWalletBodyModel(
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
        EmergencyAccessKitImportPasteMobileKeyBodyModel(
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
          headline = "Import your wallet",
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
        EmergencyAccessKitCodeNotRecognizedBodyModel(
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
        EmergencyAccessKitRestoreWalletBodyModel(
          onBack = { state = currentState.onBack(props) },
          onRestore = onRestore
        ).asRootScreen()
      }

      is StartNFCRestore -> {
        val sealedCsek =
          when (currentState.payload) {
            is EmergencyAccessKitPayload.EmergencyAccessKitPayloadV1 ->
              currentState.payload.sealedHwEncryptionKey
          }
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              Csek(
                SymmetricKeyImpl(
                  commands.unsealKey(session, sealedCsek.toUByteList()).toByteString()
                )
              )
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
            eventTrackerContext = NfcEventTrackerScreenIdContext.UNSEAL_EMERGENCY_ACCESS_KIT_BACKUP
          )
        )
      }

      is RestoreCompleting -> {
        LaunchedEffect("restoring-from-backup") {
          emergencyAccessPayloadRestorer.restoreFromPayload(currentState.payload)
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
        EmergencyAccessKitCodeNotRecognizedBodyModel(
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
      message = "Importing Emergency Exit Kit backup...",
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
      val payload: EmergencyAccessKitPayload,
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
      val payload: EmergencyAccessKitPayload,
      val entrySource: EntrySource,
    ) : State {
      fun onSuccess(): State = RestoreCompleting(payload = payload)
    }

    data class RestoreCompleting(
      val payload: EmergencyAccessKitPayload,
    ) : State

    data object RestoreFailed : State {
      fun onScanQRCode() = QrEntry

      fun onImport() = ManualEntry(enteredText = "")
    }

    data class RestoreCompleted(
      val accountRestoration: EmergencyAccessPayloadRestorer.AccountRestoration,
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

    fun onBack(props: EmergencyAccessKitRecoveryUiStateMachineProps): State {
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
