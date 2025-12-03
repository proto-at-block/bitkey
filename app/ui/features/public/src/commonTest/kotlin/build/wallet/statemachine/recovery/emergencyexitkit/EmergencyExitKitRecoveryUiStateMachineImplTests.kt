package build.wallet.statemachine.recovery.emergencyexitkit

import app.cash.turbine.test
import build.wallet.analytics.events.screen.id.EmergencyAccessKitTrackerScreenId.LOADING_BACKUP
import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.spending.AppSpendingPrivateKeyMock
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.cloud.backup.csek.Csek
import build.wallet.cloud.backup.csek.CsekDaoFake
import build.wallet.cloud.backup.csek.CsekFake
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.coroutines.turbine.turbines
import build.wallet.emergencyexitkit.EmergencyExitKitBackup
import build.wallet.emergencyexitkit.EmergencyExitKitPayload.EmergencyExitKitPayloadV1
import build.wallet.emergencyexitkit.EmergencyExitKitPayloadDecoderImpl
import build.wallet.emergencyexitkit.EmergencyExitPayloadRestorerImpl
import build.wallet.encrypt.SealedData
import build.wallet.encrypt.SymmetricKeyEncryptorFake
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.platform.clipboard.ClipItem
import build.wallet.platform.clipboard.ClipboardMock
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification
import build.wallet.statemachine.platform.permissions.PermissionUiStateMachineMock
import build.wallet.statemachine.send.QrCodeScanBodyModel
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.clickSecondaryButton
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.utils.io.core.*
import okio.ByteString.Companion.EMPTY
import okio.ByteString.Companion.toByteString

class EmergencyExitKitRecoveryUiStateMachineImplTests : FunSpec({
  val onExitCalls = turbines.create<Unit>("onExit callbacks")
  val clipboard = ClipboardMock()
  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {}
  val csekDao = CsekDaoFake()
  val keyboxDao = KeyboxDaoMock(turbines::create)
  val appPrivateKeyDao = AppPrivateKeyDaoFake()
  val symmetricKeyEncryptor = SymmetricKeyEncryptorFake()

  val props = EmergencyExitKitRecoveryUiStateMachineProps(
    onExit = { onExitCalls.add(Unit) }
  )
  val permissionMock = PermissionUiStateMachineMock()
  val stateMachine = EmergencyExitKitRecoveryUiStateMachineImpl(
    clipboard = clipboard,
    payloadDecoder = EmergencyExitKitPayloadDecoderImpl(),
    permissionUiStateMachine = permissionMock,
    emergencyExitPayloadRestorer = EmergencyExitPayloadRestorerImpl(
      csekDao = csekDao,
      symmetricKeyEncryptor = symmetricKeyEncryptor,
      appPrivateKeyDao = appPrivateKeyDao,
      emergencyExitKitPayloadDecoder = EmergencyExitKitPayloadDecoderImpl()
    ),
    csekDao = csekDao,
    keyboxDao = keyboxDao,
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    uuidGenerator = UuidGeneratorFake()
  )

  lateinit var validData: String
  beforeSpec {
    validData = EmergencyExitKitPayloadDecoderImpl().encode(
      EmergencyExitKitPayloadV1(
        sealedHwEncryptionKey = SealedCsekFake,
        sealedActiveSpendingKeys = SealedData(
          ciphertext = EmergencyExitKitPayloadDecoderImpl().encodeBackup(
            EmergencyExitKitBackup.EmergencyExitKitBackupV1(
              spendingKeyset = SpendingKeysetMock,
              appSpendingKeyXprv = AppSpendingPrivateKeyMock
            )
          ),
          nonce = "nonce".toByteArray().toByteString(),
          tag = EMPTY
        )
      )
    )
  }

  beforeTest {
    permissionMock.isImplemented = true
    csekDao.reset()
    symmetricKeyEncryptor.reset()
    appPrivateKeyDao.reset()
    keyboxDao.reset()
  }

  test("UI select input method - back exits flow") {
    stateMachine.test(props = props) {
      awaitBody<EmergencyExitKitImportWalletBodyModel> {
        onBack()
      }
      onExitCalls.awaitItem()
    }
  }

  test("UI manual entry - Decoding failure - preserves entered text") {
    val invalidData = "Invalid payload!"
    stateMachine.test(props = props) {
      awaitBody<EmergencyExitKitImportWalletBodyModel> {
        onEnterManually()
      }
      awaitBody<EmergencyExitKitImportPasteAppKeyBodyModel> {
        enteredText.shouldBeEmpty()
        onEnterTextChanged(invalidData)
      }
      awaitBody<EmergencyExitKitImportPasteAppKeyBodyModel> {
        enteredText.shouldBe(invalidData)
        onContinue()
      }
      awaitBody<EmergencyExitKitCodeNotRecognizedBodyModel> {
        arrivedFromManualEntry.shouldBeTrue()
        onBack()
      }
      awaitBody<EmergencyExitKitImportPasteAppKeyBodyModel> {
        enteredText.shouldBe(invalidData)
        onContinue()
      }
      awaitBody<EmergencyExitKitCodeNotRecognizedBodyModel> {
        onImport()
      }
      awaitBody<EmergencyExitKitImportPasteAppKeyBodyModel> {
        enteredText.shouldBe(invalidData)
      }
    }
  }

  test("UI manual entry - Successful Decoding") {
    stateMachine.test(props = props) {
      awaitBody<EmergencyExitKitImportWalletBodyModel> {
        onEnterManually()
      }
      awaitBody<EmergencyExitKitImportPasteAppKeyBodyModel> {
        onEnterTextChanged(validData)
      }
      awaitBody<EmergencyExitKitImportPasteAppKeyBodyModel> {
        enteredText.shouldBe(validData)
        onContinue()
      }
      awaitBody<EmergencyExitKitRestoreWalletBodyModel>()
    }
  }

  test("UI manual entry - Back until exit") {
    stateMachine.test(props = props) {
      awaitBody<EmergencyExitKitImportWalletBodyModel> {
        onEnterManually()
      }
      awaitBody<EmergencyExitKitImportPasteAppKeyBodyModel> {
        onEnterTextChanged(validData)
      }
      awaitBody<EmergencyExitKitImportPasteAppKeyBodyModel> {
        enteredText.shouldBe(validData)
        onContinue()
      }
      awaitBody<EmergencyExitKitRestoreWalletBodyModel> {
        onBack()
      }
      awaitBody<EmergencyExitKitImportPasteAppKeyBodyModel> {
        onBack()
      }
      awaitBody<EmergencyExitKitImportWalletBodyModel> {
        onBack()
      }
      onExitCalls.awaitItem()
    }
  }

  test("UI Manual Entry - Paste") {
    clipboard.setItem(ClipItem.PlainText(validData))
    stateMachine.test(props = props) {
      awaitBody<EmergencyExitKitImportWalletBodyModel> {
        onEnterManually()
      }
      awaitBody<EmergencyExitKitImportPasteAppKeyBodyModel> {
        onPasteButtonClick()
      }
      awaitBody<EmergencyExitKitImportPasteAppKeyBodyModel> {
        enteredText.shouldBe(validData)
      }
    }
  }

  test("UI QR Code - Successful Decoding") {
    stateMachine.test(props = props) {
      permissionMock.isImplemented = false
      awaitBody<EmergencyExitKitImportWalletBodyModel> {
        onScanQRCode()
      }
      awaitBody<QrCodeScanBodyModel> {
        onQrCodeScanned(validData)
      }
      awaitBody<EmergencyExitKitRestoreWalletBodyModel>()
    }
  }

  test("UI QR Code - Error Decoding") {
    permissionMock.isImplemented = false
    val invalidData = "Invalid payload!"
    stateMachine.test(props = props) {
      awaitBody<EmergencyExitKitImportWalletBodyModel> {
        onScanQRCode()
      }
      awaitBody<QrCodeScanBodyModel> {
        onQrCodeScanned(invalidData)
      }
      awaitBody<EmergencyExitKitCodeNotRecognizedBodyModel> {
        onImport()
      }
      awaitBody<EmergencyExitKitImportPasteAppKeyBodyModel>()
    }
  }

  test("UI Manual Entry - Successful Restore") {
    stateMachine.testWithVirtualTime(props = props) {
      awaitBody<EmergencyExitKitImportWalletBodyModel> {
        onEnterManually()
      }
      awaitBody<EmergencyExitKitImportPasteAppKeyBodyModel> {
        onEnterTextChanged(validData)
      }
      awaitBody<EmergencyExitKitImportPasteAppKeyBodyModel> {
        enteredText.shouldBe(validData)
        onContinue()
      }
      awaitUntilBody<EmergencyExitKitRestoreWalletBodyModel>(
        matching = { it.onRestore != null }
      ) {
        onRestore.shouldNotBeNull().invoke()
      }
      // Unsealing CSEK
      awaitBodyMock<NfcSessionUIStateMachineProps<Csek>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(CsekFake)
      }

      // Decoding backup and attempting to apply
      awaitBody<LoadingSuccessBodyModel>(LOADING_BACKUP) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)

        // There should be an active keybox.
        keyboxDao.activeKeybox.test {
          awaitUntil { it.get() != null }
        }

        val restoredPrivateKey = appPrivateKeyDao.getAppSpendingPrivateKey(AppSpendingPublicKeyMock)
          .get()
          .shouldNotBeNull()

        // App spending private key should be set.
        restoredPrivateKey.key.xprv
          .shouldBeEqual(AppSpendingPrivateKeyMock.key.xprv)
      }
    }
  }

  test("Failed Restore - decrypt failed") {
    // Force the encryptor to throw an error while attempting decryption.
    // This is the same real behavior of the encryptor when provided with an invalid ciphertext.
    symmetricKeyEncryptor.unsealError = true

    stateMachine.test(props = props) {
      awaitBody<EmergencyExitKitImportWalletBodyModel> {
        onEnterManually()
      }
      awaitBody<EmergencyExitKitImportPasteAppKeyBodyModel> {
        onEnterTextChanged(validData)
      }
      awaitBody<EmergencyExitKitImportPasteAppKeyBodyModel> {
        enteredText.shouldBe(validData)
        onContinue()
      }
      awaitUntilBody<EmergencyExitKitRestoreWalletBodyModel>(
        matching = { it.onRestore != null }
      ) {
        onRestore.shouldNotBeNull().invoke()
      }
      // Unsealing CSEK
      awaitBodyMock<NfcSessionUIStateMachineProps<Csek>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(CsekFake)
      }

      awaitBody<LoadingSuccessBodyModel>(LOADING_BACKUP) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<EmergencyExitKitCodeNotRecognizedBodyModel>()
    }
  }

  test("Failed Restore - invalid backup") {
    val invalidPayload =
      EmergencyExitKitPayloadDecoderImpl().encode(
        EmergencyExitKitPayloadV1(
          sealedHwEncryptionKey = "ciphertext".toByteArray().toByteString(),
          sealedActiveSpendingKeys =
            SealedData(
              ciphertext = "sealedCipherText".toByteArray().toByteString(),
              nonce = "nonce".toByteArray().toByteString(),
              tag = EMPTY
            )
        )
      )
    stateMachine.test(props = props) {
      awaitBody<EmergencyExitKitImportWalletBodyModel> {
        clickSecondaryButton()
      }
      awaitBody<EmergencyExitKitImportPasteAppKeyBodyModel> {
        onEnterTextChanged(invalidPayload)
      }
      awaitBody<EmergencyExitKitImportPasteAppKeyBodyModel> {
        enteredText.shouldBe(invalidPayload)
        onContinue()
      }
      awaitUntilBody<EmergencyExitKitRestoreWalletBodyModel>(
        matching = { it.onRestore != null }
      ) {
        onRestore.shouldNotBeNull().invoke()
      }
      // Unsealing CSEK
      awaitBodyMock<NfcSessionUIStateMachineProps<Csek>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(CsekFake)
      }

      awaitBody<LoadingSuccessBodyModel>(LOADING_BACKUP) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<EmergencyExitKitCodeNotRecognizedBodyModel>()
    }
  }

  test("EEK recovery uses NotRequired for hardware verification") {
    stateMachine.test(props = props) {
      awaitBody<EmergencyExitKitImportWalletBodyModel> {
        onEnterManually()
      }
      awaitBody<EmergencyExitKitImportPasteAppKeyBodyModel> {
        onEnterTextChanged(validData)
      }
      awaitBody<EmergencyExitKitImportPasteAppKeyBodyModel> {
        enteredText.shouldBe(validData)
        onContinue()
      }
      awaitUntilBody<EmergencyExitKitRestoreWalletBodyModel>(
        matching = { it.onRestore != null }
      ) {
        onRestore.shouldNotBeNull().invoke()
      }

      // Verify that EEK recovery now correctly uses NotRequired for hardware verification
      // This ensures that the hardware pairing check won't interfere when there's no active account
      awaitBodyMock<NfcSessionUIStateMachineProps<Csek>>(
        id = nfcSessionUIStateMachine.id
      ) {
        // The hardware verification should be NotRequired for EEK recovery
        hardwareVerification.shouldBeTypeOf<HardwareVerification.NotRequired>()

        // Continue with successful NFC operation
        onSuccess(CsekFake)
      }

      awaitBody<LoadingSuccessBodyModel>(LOADING_BACKUP) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }
  }
})
