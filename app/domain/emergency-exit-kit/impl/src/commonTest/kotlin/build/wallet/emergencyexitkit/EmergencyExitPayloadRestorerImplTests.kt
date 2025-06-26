package build.wallet.emergencyexitkit

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.AppSpendingPrivateKeyMock
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.cloud.backup.csek.CsekDaoFake
import build.wallet.cloud.backup.csek.CsekFake
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.emergencyexitkit.EmergencyExitPayloadRestorer.EmergencyExitPayloadRestorerError.CsekMissing
import build.wallet.emergencyexitkit.EmergencyExitPayloadRestorer.EmergencyExitPayloadRestorerError.InvalidBackup
import build.wallet.encrypt.SealedData
import build.wallet.testing.shouldBeErr
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import okio.ByteString

class EmergencyExitPayloadRestorerImplTests : FunSpec({

  val csekDao = CsekDaoFake()
  val symmetricKeyEncryptor = SymmetricKeyEncryptorFake()
  val appPrivateKeyDao = AppPrivateKeyDaoFake()

  val creator =
    EmergencyExitPayloadCreatorImpl(
      csekDao = csekDao,
      symmetricKeyEncryptor = symmetricKeyEncryptor,
      appPrivateKeyDao = appPrivateKeyDao,
      emergencyExitKitPayloadDecoder = EmergencyExitKitPayloadDecoderImpl()
    )

  val restorer =
    EmergencyExitPayloadRestorerImpl(
      csekDao = csekDao,
      symmetricKeyEncryptor = symmetricKeyEncryptor,
      appPrivateKeyDao = appPrivateKeyDao,
      emergencyExitKitPayloadDecoder = EmergencyExitKitPayloadDecoderImpl()
    )

  afterTest {
    csekDao.reset()
    appPrivateKeyDao.reset()
  }

  test("Full create and restore loop") {
    csekDao.set(SealedCsekFake, CsekFake)
    appPrivateKeyDao.storeAppSpendingKeyPair(
      AppSpendingKeypair(
        publicKey = AppSpendingPublicKeyMock,
        privateKey = AppSpendingPrivateKeyMock
      )
    )

    val payload =
      creator.create(
        keybox = KeyboxMock,
        sealedCsek = SealedCsekFake
      )
        .get()
        .shouldNotBeNull()

    appPrivateKeyDao.reset()

    restorer.restoreFromPayload(payload = payload)
      .get()
      .shouldNotBeNull()

    val restoredPrivateKey =
      appPrivateKeyDao.getAppSpendingPrivateKey(AppSpendingPublicKeyMock)
        .get()
        .shouldNotBeNull()

    restoredPrivateKey.key.xprv
      .shouldBeEqual(AppSpendingPrivateKeyMock.key.xprv)
  }

  test("Restore fails with invalid sealed backup data") {
    csekDao.set(SealedCsekFake, CsekFake)

    restorer.restoreFromPayload(
      payload =
        EmergencyExitKitPayload.EmergencyExitKitPayloadV1(
          sealedActiveSpendingKeys =
            SealedData(
              ciphertext = ByteString.EMPTY,
              nonce = ByteString.EMPTY,
              tag = ByteString.EMPTY
            ),
          sealedHwEncryptionKey = CsekFake.key.raw
        )
    )
      .shouldBeErr(
        InvalidBackup(cause = EmergencyExitKitPayloadDecoder.DecodeError.InvalidProtoData())
      )
  }

  test("Restore fails when missing the unsealed Csek") {
    csekDao.set(SealedCsekFake, CsekFake)
    appPrivateKeyDao.storeAppSpendingKeyPair(
      AppSpendingKeypair(
        publicKey = AppSpendingPublicKeyMock,
        privateKey = AppSpendingPrivateKeyMock
      )
    )

    val payload =
      creator.create(
        keybox = KeyboxMock,
        sealedCsek = SealedCsekFake
      )
        .get()
        .shouldNotBeNull()

    csekDao.reset()

    restorer.restoreFromPayload(payload)
      .shouldBeErr(CsekMissing())
  }

  test("convert account restoration in keybox") {
    csekDao.set(SealedCsekFake, CsekFake)
    appPrivateKeyDao.storeAppSpendingKeyPair(
      AppSpendingKeypair(
        publicKey = AppSpendingPublicKeyMock,
        privateKey = AppSpendingPrivateKeyMock
      )
    )

    val payload =
      creator.create(
        keybox = KeyboxMock,
        sealedCsek = SealedCsekFake
      )
        .get()
        .shouldNotBeNull()

    val extracted = restorer.restoreFromPayload(payload)
      .get()
      .shouldNotBeNull()

    val keybox = extracted.asKeybox(
      keyboxId = "keyboxId",
      appKeyBundleId = "appKeyBundleId",
      hwKeyBundleId = "hwKeyBundleId"
    ).shouldNotBeNull()

    keybox.activeAppKeyBundle.spendingKey
      .shouldBeEqual(AppSpendingPublicKeyMock)
  }
})
