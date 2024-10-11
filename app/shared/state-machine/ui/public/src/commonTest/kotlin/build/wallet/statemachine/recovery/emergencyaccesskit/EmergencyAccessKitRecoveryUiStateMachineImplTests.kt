package build.wallet.statemachine.recovery.emergencyaccesskit

import build.wallet.analytics.events.screen.id.EmergencyAccessKitTrackerScreenId.LOADING_BACKUP
import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.spending.AppSpendingPrivateKeyMock
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.cloud.backup.csek.Csek
import build.wallet.cloud.backup.csek.CsekDaoFake
import build.wallet.cloud.backup.csek.CsekFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.crypto.SymmetricKey
import build.wallet.debug.DebugOptionsServiceFake
import build.wallet.emergencyaccesskit.EmergencyAccessKitBackup
import build.wallet.emergencyaccesskit.EmergencyAccessKitPayload.EmergencyAccessKitPayloadV1
import build.wallet.emergencyaccesskit.EmergencyAccessKitPayloadDecoderImpl
import build.wallet.emergencyaccesskit.EmergencyAccessPayloadRestorerImpl
import build.wallet.encrypt.SealedData
import build.wallet.encrypt.SymmetricKeyEncryptor
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.platform.clipboard.ClipItem
import build.wallet.platform.clipboard.ClipboardMock
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.platform.permissions.PermissionUiStateMachineMock
import build.wallet.statemachine.send.QrCodeScanBodyModel
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
import com.github.michaelbull.result.get
import com.github.michaelbull.result.unwrap
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.ktor.utils.io.core.*
import okio.ByteString
import okio.ByteString.Companion.EMPTY
import okio.ByteString.Companion.toByteString

class EmergencyAccessKitRecoveryUiStateMachineImplTests : FunSpec({
  val onExitCalls = turbines.create<Unit>("onExit callbacks")
  val clipboard = ClipboardMock()
  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {}
  val csekDao = CsekDaoFake()
  val keyboxDao = KeyboxDaoMock(turbines::create)
  val appPrivateKeyDao = AppPrivateKeyDaoFake()
  val symmetricKeyEncryptor =
    object : SymmetricKeyEncryptor {
      var shouldFail = false
      lateinit var sealedData: SealedData

      override fun seal(
        unsealedData: ByteString,
        key: SymmetricKey,
      ): SealedData {
        sealedData = SealedData(
          ciphertext = unsealedData,
          nonce = key.raw,
          tag = EMPTY
        )
        return sealedData
      }

      override fun unseal(
        sealedData: SealedData,
        key: SymmetricKey,
      ): ByteString {
        if (shouldFail) {
          throw IllegalStateException()
        }
        return sealedData.ciphertext
      }

      fun reset() {
        sealedData = SealedData(
          ciphertext = EMPTY,
          nonce = EMPTY,
          tag = EMPTY
        )
      }
    }

  val props = EmergencyAccessKitRecoveryUiStateMachineProps(
    onExit = { onExitCalls.add(Unit) }
  )
  val permissionMock = PermissionUiStateMachineMock()
  val debugOptionsService = DebugOptionsServiceFake()
  val stateMachine = EmergencyAccessKitRecoveryUiStateMachineImpl(
    clipboard = clipboard,
    payloadDecoder = EmergencyAccessKitPayloadDecoderImpl,
    permissionUiStateMachine = permissionMock,
    emergencyAccessPayloadRestorer = EmergencyAccessPayloadRestorerImpl(
      csekDao = csekDao,
      symmetricKeyEncryptor = symmetricKeyEncryptor,
      appPrivateKeyDao = appPrivateKeyDao
    ),
    csekDao = csekDao,
    keyboxDao = keyboxDao,
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    uuidGenerator = UuidGeneratorFake(),
    debugOptionsService = debugOptionsService
  )
  val validData = EmergencyAccessKitPayloadDecoderImpl.encode(
    EmergencyAccessKitPayloadV1(
      sealedHwEncryptionKey = CsekFake.key.raw,
      sealedActiveSpendingKeys = SealedData(
        ciphertext = EmergencyAccessKitPayloadDecoderImpl.encodeBackup(
          EmergencyAccessKitBackup.EmergencyAccessKitBackupV1(
            spendingKeyset = SpendingKeysetMock,
            appSpendingKeyXprv = AppSpendingPrivateKeyMock
          )
        ),
        nonce = "nonce".toByteArray().toByteString(),
        tag = EMPTY
      )
    )
  )

  beforeTest {
    permissionMock.isImplemented = true
    csekDao.reset()
    symmetricKeyEncryptor.reset()
    appPrivateKeyDao.reset()
    keyboxDao.reset()
  }

  test("UI select input method - back exits flow") {
    stateMachine.test(props = props) {
      awaitScreenWithBody<EmergencyAccessKitImportWalletBodyModel> {
        onBack()
      }
      onExitCalls.awaitItem()
    }
  }

  test("UI manual entry - Decoding failure - preserves entered text") {
    val invalidData = "Invalid payload!"
    stateMachine.test(props = props) {
      awaitScreenWithBody<EmergencyAccessKitImportWalletBodyModel> {
        onEnterManually()
      }
      awaitScreenWithBody<EmergencyAccessKitImportPasteMobileKeyBodyModel> {
        enteredText.shouldBeEmpty()
        onEnterTextChanged(invalidData)
      }
      awaitScreenWithBody<EmergencyAccessKitImportPasteMobileKeyBodyModel> {
        enteredText.shouldBe(invalidData)
        onContinue()
      }
      awaitScreenWithBody<EmergencyAccessKitCodeNotRecognizedBodyModel> {
        arrivedFromManualEntry.shouldBeTrue()
        onBack()
      }
      awaitScreenWithBody<EmergencyAccessKitImportPasteMobileKeyBodyModel> {
        enteredText.shouldBe(invalidData)
        onContinue()
      }
      awaitScreenWithBody<EmergencyAccessKitCodeNotRecognizedBodyModel> {
        onImport()
      }
      awaitScreenWithBody<EmergencyAccessKitImportPasteMobileKeyBodyModel> {
        enteredText.shouldBe(invalidData)
      }
    }
  }

  test("UI manual entry - Successful Decoding") {
    stateMachine.test(props = props) {
      awaitScreenWithBody<EmergencyAccessKitImportWalletBodyModel> {
        onEnterManually()
      }
      awaitScreenWithBody<EmergencyAccessKitImportPasteMobileKeyBodyModel> {
        onEnterTextChanged(validData)
      }
      awaitScreenWithBody<EmergencyAccessKitImportPasteMobileKeyBodyModel> {
        enteredText.shouldBe(validData)
        onContinue()
      }
      awaitScreenWithBody<EmergencyAccessKitRestoreWalletBodyModel>()
    }
  }

  test("UI manual entry - Back until exit") {
    stateMachine.test(props = props) {
      awaitScreenWithBody<EmergencyAccessKitImportWalletBodyModel> {
        onEnterManually()
      }
      awaitScreenWithBody<EmergencyAccessKitImportPasteMobileKeyBodyModel> {
        onEnterTextChanged(validData)
      }
      awaitScreenWithBody<EmergencyAccessKitImportPasteMobileKeyBodyModel> {
        enteredText.shouldBe(validData)
        onContinue()
      }
      awaitScreenWithBody<EmergencyAccessKitRestoreWalletBodyModel> {
        onBack()
      }
      awaitScreenWithBody<EmergencyAccessKitImportPasteMobileKeyBodyModel> {
        onBack()
      }
      awaitScreenWithBody<EmergencyAccessKitImportWalletBodyModel> {
        onBack()
      }
      onExitCalls.awaitItem()
    }
  }

  test("UI Manual Entry - Paste") {
    clipboard.setItem(ClipItem.PlainText(validData))
    stateMachine.test(props = props) {
      awaitScreenWithBody<EmergencyAccessKitImportWalletBodyModel> {
        onEnterManually()
      }
      awaitScreenWithBody<EmergencyAccessKitImportPasteMobileKeyBodyModel> {
        onPasteButtonClick()
      }
      awaitScreenWithBody<EmergencyAccessKitImportPasteMobileKeyBodyModel> {
        enteredText.shouldBe(validData)
      }
    }
  }

  test("UI QR Code - Successful Decoding") {
    stateMachine.test(props = props) {
      permissionMock.isImplemented = false
      awaitScreenWithBody<EmergencyAccessKitImportWalletBodyModel> {
        onScanQRCode()
      }
      awaitScreenWithBody<QrCodeScanBodyModel> {
        onQrCodeScanned(validData)
      }
      awaitScreenWithBody<EmergencyAccessKitRestoreWalletBodyModel>()
    }
  }

  test("UI QR Code - Error Decoding") {
    permissionMock.isImplemented = false
    val invalidData = "Invalid payload!"
    stateMachine.test(props = props) {
      awaitScreenWithBody<EmergencyAccessKitImportWalletBodyModel> {
        onScanQRCode()
      }
      awaitScreenWithBody<QrCodeScanBodyModel> {
        onQrCodeScanned(invalidData)
      }
      awaitScreenWithBody<EmergencyAccessKitCodeNotRecognizedBodyModel> {
        onImport()
      }
      awaitScreenWithBody<EmergencyAccessKitImportPasteMobileKeyBodyModel>()
    }
  }

  test("UI Manual Entry - Successful Restore") {
    stateMachine.test(props = props) {
      awaitScreenWithBody<EmergencyAccessKitImportWalletBodyModel> {
        onEnterManually()
      }
      awaitScreenWithBody<EmergencyAccessKitImportPasteMobileKeyBodyModel> {
        onEnterTextChanged(validData)
      }
      awaitScreenWithBody<EmergencyAccessKitImportPasteMobileKeyBodyModel> {
        enteredText.shouldBe(validData)
        onContinue()
      }
      awaitUntilScreenWithBody<EmergencyAccessKitRestoreWalletBodyModel>(
        expectedBodyContentMatch = { it.onRestore != null }
      ) {
        onRestore.shouldNotBeNull().invoke()
      }
      // Unsealing CSEK
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<Csek>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(CsekFake)
      }

      // Decoding backup and attempting to apply
      awaitScreenWithBody<LoadingSuccessBodyModel>(LOADING_BACKUP) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)

        val restoredPrivateKey = appPrivateKeyDao.getAppSpendingPrivateKey(AppSpendingPublicKeyMock)
          .get()
          .shouldNotBeNull()

        // App spending private key should be set.
        restoredPrivateKey.key.xprv
          .shouldBeEqual(AppSpendingPrivateKeyMock.key.xprv)

        // There should be an active keybox.
        keyboxDao.activeKeybox.value.unwrap().shouldNotBeNull()
      }
    }
  }

  test("Failed Restore - decrypt failed") {
    // Force the encryptor to throw an error while attempting decryption.
    // This is the same real behavior of the encryptor when provided with an invalid ciphertext.
    symmetricKeyEncryptor.shouldFail = true

    stateMachine.test(props = props) {
      awaitScreenWithBody<EmergencyAccessKitImportWalletBodyModel> {
        onEnterManually()
      }
      awaitScreenWithBody<EmergencyAccessKitImportPasteMobileKeyBodyModel> {
        onEnterTextChanged(validData)
      }
      awaitScreenWithBody<EmergencyAccessKitImportPasteMobileKeyBodyModel> {
        enteredText.shouldBe(validData)
        onContinue()
      }
      awaitUntilScreenWithBody<EmergencyAccessKitRestoreWalletBodyModel>(
        expectedBodyContentMatch = { it.onRestore != null }
      ) {
        onRestore.shouldNotBeNull().invoke()
      }
      // Unsealing CSEK
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<Csek>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(CsekFake)
      }

      awaitScreenWithBody<LoadingSuccessBodyModel>(LOADING_BACKUP) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitScreenWithBody<EmergencyAccessKitCodeNotRecognizedBodyModel>()
    }
  }

  test("Failed Restore - invalid backup") {
    val invalidPayload =
      EmergencyAccessKitPayloadDecoderImpl.encode(
        EmergencyAccessKitPayloadV1(
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
      awaitScreenWithBody<EmergencyAccessKitImportWalletBodyModel> {
        clickPrimaryButton()
      }
      awaitScreenWithBody<EmergencyAccessKitImportPasteMobileKeyBodyModel> {
        onEnterTextChanged(invalidPayload)
      }
      awaitScreenWithBody<EmergencyAccessKitImportPasteMobileKeyBodyModel> {
        enteredText.shouldBe(invalidPayload)
        onContinue()
      }
      awaitUntilScreenWithBody<EmergencyAccessKitRestoreWalletBodyModel>(
        expectedBodyContentMatch = { it.onRestore != null }
      ) {
        onRestore.shouldNotBeNull().invoke()
      }
      // Unsealing CSEK
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<Csek>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(CsekFake)
      }

      awaitScreenWithBody<LoadingSuccessBodyModel>(LOADING_BACKUP) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitScreenWithBody<EmergencyAccessKitCodeNotRecognizedBodyModel>()
    }
  }
})
