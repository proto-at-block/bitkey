package build.wallet.statemachine.recovery.emergencyaccesskit

import build.wallet.analytics.events.screen.id.EmergencyAccessKitTrackerScreenId.CODE_NOT_RECOGNIZED
import build.wallet.analytics.events.screen.id.EmergencyAccessKitTrackerScreenId.IMPORT_TEXT_KEY
import build.wallet.analytics.events.screen.id.EmergencyAccessKitTrackerScreenId.LOADING_BACKUP
import build.wallet.analytics.events.screen.id.EmergencyAccessKitTrackerScreenId.RESTORE_YOUR_WALLET
import build.wallet.analytics.events.screen.id.EmergencyAccessKitTrackerScreenId.SCAN_QR_CODE
import build.wallet.analytics.events.screen.id.EmergencyAccessKitTrackerScreenId.SELECT_IMPORT_METHOD
import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.keybox.FullAccountConfigMock
import build.wallet.bitkey.spending.AppSpendingPrivateKeyMock
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.cloud.backup.csek.Csek
import build.wallet.cloud.backup.csek.CsekDaoFake
import build.wallet.cloud.backup.csek.CsekFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.crypto.SymmetricKey
import build.wallet.emergencyaccesskit.EmergencyAccessKitBackup
import build.wallet.emergencyaccesskit.EmergencyAccessKitPayload.EmergencyAccessKitPayloadV1
import build.wallet.emergencyaccesskit.EmergencyAccessKitPayloadDecoderImpl
import build.wallet.emergencyaccesskit.EmergencyAccessPayloadRestorerImpl
import build.wallet.encrypt.SealedData
import build.wallet.encrypt.SymmetricKeyEncryptor
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.platform.clipboard.ClipItem
import build.wallet.platform.clipboard.ClipboardMock
import build.wallet.platform.random.UuidFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.platform.permissions.PermissionUiStateMachineMock
import build.wallet.statemachine.send.QrCodeScanBodyModel
import build.wallet.statemachine.ui.clickPrimaryButton
import com.github.michaelbull.result.get
import com.github.michaelbull.result.unwrap
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.utils.io.core.toByteArray
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

  val props =
    EmergencyAccessKitRecoveryUiStateMachineProps(
      fullAccountConfig = FullAccountConfigMock,
      onExit = { onExitCalls.add(Unit) }
    )
  val permissionMock = PermissionUiStateMachineMock()
  val stateMachine =
    EmergencyAccessKitRecoveryUiStateMachineImpl(
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
      uuid = UuidFake()
    )
  val validData =
    EmergencyAccessKitPayloadDecoderImpl.encode(
      EmergencyAccessKitPayloadV1(
        sealedHwEncryptionKey = CsekFake.key.raw,
        sealedActiveSpendingKeys =
          SealedData(
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
      awaitScreenWithBody<FormBodyModel>(SELECT_IMPORT_METHOD) {
        this.onBack.shouldNotBeNull().invoke()
      }
      onExitCalls.awaitItem()
    }
  }

  test("UI manual entry - Decoding failure - preserves entered text") {
    val invalidData = "Invalid payload!"
    stateMachine.test(props = props) {
      awaitScreenWithBody<FormBodyModel>(SELECT_IMPORT_METHOD) {
        clickPrimaryButton()
      }
      awaitScreenWithBody<FormBodyModel>(IMPORT_TEXT_KEY) {
        val fieldModel =
          this.mainContentList.first()
            .shouldBeTypeOf<FormMainContentModel.AddressInput>()
            .fieldModel

        fieldModel
          .value
          .shouldBe("")

        fieldModel.onValueChange(invalidData, IntRange(0, invalidData.length))
      }
      awaitScreenWithBody<FormBodyModel>(IMPORT_TEXT_KEY) {
        this.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.AddressInput>()
          .fieldModel
          .value
          .shouldBe(invalidData)

        clickPrimaryButton()
      }
      awaitScreenWithBody<FormBodyModel>(CODE_NOT_RECOGNIZED) {
        this.secondaryButton.shouldNotBeNull().text.shouldBe("Try again")
        this.onBack.shouldNotBeNull().invoke()
      }
      awaitScreenWithBody<FormBodyModel>(IMPORT_TEXT_KEY) {
        this.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.AddressInput>()
          .fieldModel
          .value
          .shouldBe(invalidData)

        clickPrimaryButton()
      }
      awaitScreenWithBody<FormBodyModel>(CODE_NOT_RECOGNIZED) {
        this
          .secondaryButton
          .shouldNotBeNull()
          .onClick()
      }
      awaitScreenWithBody<FormBodyModel>(IMPORT_TEXT_KEY) {
        this.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.AddressInput>()
          .fieldModel
          .value
          .shouldBe(invalidData)
      }
    }
  }

  test("UI manual entry - Successful Decoding") {
    stateMachine.test(props = props) {
      awaitScreenWithBody<FormBodyModel>(SELECT_IMPORT_METHOD) {
        clickPrimaryButton()
      }
      awaitScreenWithBody<FormBodyModel>(IMPORT_TEXT_KEY) {
        this.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.AddressInput>()
          .fieldModel
          .onValueChange(validData, IntRange(0, validData.length))
      }
      awaitScreenWithBody<FormBodyModel>(IMPORT_TEXT_KEY) {
        this.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.AddressInput>()
          .fieldModel
          .value
          .shouldBe(validData)

        clickPrimaryButton()
      }
      awaitScreenWithBody<FormBodyModel>(RESTORE_YOUR_WALLET)
    }
  }

  test("UI manual entry - Back until exit") {
    stateMachine.test(props = props) {
      awaitScreenWithBody<FormBodyModel>(SELECT_IMPORT_METHOD) {
        clickPrimaryButton()
      }
      awaitScreenWithBody<FormBodyModel>(IMPORT_TEXT_KEY) {
        this.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.AddressInput>()
          .fieldModel
          .onValueChange(validData, IntRange(0, validData.length))
      }
      awaitScreenWithBody<FormBodyModel>(IMPORT_TEXT_KEY) {
        this.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.AddressInput>()
          .fieldModel
          .value
          .shouldBe(validData)

        clickPrimaryButton()
      }
      awaitScreenWithBody<FormBodyModel>(RESTORE_YOUR_WALLET) {
        this.onBack.shouldNotBeNull().invoke()
      }
      awaitScreenWithBody<FormBodyModel>(IMPORT_TEXT_KEY) {
        this.onBack.shouldNotBeNull().invoke()
      }
      awaitScreenWithBody<FormBodyModel>(SELECT_IMPORT_METHOD) {
        this.onBack.shouldNotBeNull().invoke()
      }
      onExitCalls.awaitItem()
    }
  }

  test("UI Manual Entry - Paste") {
    clipboard.setItem(ClipItem.PlainText(validData))
    stateMachine.test(props = props) {
      awaitScreenWithBody<FormBodyModel>(SELECT_IMPORT_METHOD) {
        clickPrimaryButton()
      }
      awaitScreenWithBody<FormBodyModel>(IMPORT_TEXT_KEY) {
        this.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.AddressInput>()
          .trailingButtonModel
          .shouldNotBeNull()
          .onClick()
      }
      awaitScreenWithBody<FormBodyModel>(IMPORT_TEXT_KEY) {
        this.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.AddressInput>()
          .fieldModel
          .value
          .shouldBe(validData)
      }
    }
  }

  test("UI QR Code - Successful Decoding") {
    stateMachine.test(props = props) {
      permissionMock.isImplemented = false
      awaitScreenWithBody<FormBodyModel>(SELECT_IMPORT_METHOD) {
        this.secondaryButton.shouldNotBeNull().onClick()
      }
      awaitScreenWithBody<QrCodeScanBodyModel>(SCAN_QR_CODE) {
        onQrCodeScanned(validData)
      }
      awaitScreenWithBody<FormBodyModel>(RESTORE_YOUR_WALLET)
    }
  }

  test("UI QR Code - Error Decoding") {
    permissionMock.isImplemented = false
    val invalidData = "Invalid payload!"
    stateMachine.test(props = props) {
      awaitScreenWithBody<FormBodyModel>(SELECT_IMPORT_METHOD) {
        this.secondaryButton.shouldNotBeNull().onClick()
      }
      awaitScreenWithBody<QrCodeScanBodyModel>(SCAN_QR_CODE) {
        onQrCodeScanned(invalidData)
      }
      awaitScreenWithBody<FormBodyModel>(CODE_NOT_RECOGNIZED) {
        this
          .secondaryButton
          .shouldNotBeNull()
          .onClick()
      }
      awaitScreenWithBody<FormBodyModel>(IMPORT_TEXT_KEY)
    }
  }

  test("Successful Restore") {
    stateMachine.test(props = props) {
      awaitScreenWithBody<FormBodyModel>(SELECT_IMPORT_METHOD) {
        clickPrimaryButton()
      }
      awaitScreenWithBody<FormBodyModel>(IMPORT_TEXT_KEY) {
        this.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.AddressInput>()
          .fieldModel
          .onValueChange(validData, IntRange(0, validData.length))
      }
      awaitScreenWithBody<FormBodyModel>(IMPORT_TEXT_KEY) {
        this.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.AddressInput>()
          .fieldModel
          .value
          .shouldBe(validData)

        clickPrimaryButton()
      }
      awaitScreenWithBody<FormBodyModel>(RESTORE_YOUR_WALLET) {
        clickPrimaryButton()
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
      awaitScreenWithBody<FormBodyModel>(SELECT_IMPORT_METHOD) {
        clickPrimaryButton()
      }
      awaitScreenWithBody<FormBodyModel>(IMPORT_TEXT_KEY) {
        this.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.AddressInput>()
          .fieldModel
          .onValueChange(validData, IntRange(0, validData.length))
      }
      awaitScreenWithBody<FormBodyModel>(IMPORT_TEXT_KEY) {
        this.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.AddressInput>()
          .fieldModel
          .value
          .shouldBe(validData)

        clickPrimaryButton()
      }
      awaitScreenWithBody<FormBodyModel>(RESTORE_YOUR_WALLET) {
        clickPrimaryButton()
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
      awaitScreenWithBody<FormBodyModel>(CODE_NOT_RECOGNIZED)
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
      awaitScreenWithBody<FormBodyModel>(SELECT_IMPORT_METHOD) {
        clickPrimaryButton()
      }
      awaitScreenWithBody<FormBodyModel>(IMPORT_TEXT_KEY) {
        this.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.AddressInput>()
          .fieldModel
          .onValueChange(invalidPayload, IntRange(0, invalidPayload.length))
      }
      awaitScreenWithBody<FormBodyModel>(IMPORT_TEXT_KEY) {
        this.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.AddressInput>()
          .fieldModel
          .value
          .shouldBe(invalidPayload)

        clickPrimaryButton()
      }
      awaitScreenWithBody<FormBodyModel>(RESTORE_YOUR_WALLET) {
        clickPrimaryButton()
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
      awaitScreenWithBody<FormBodyModel>(CODE_NOT_RECOGNIZED)
    }
  }
})
