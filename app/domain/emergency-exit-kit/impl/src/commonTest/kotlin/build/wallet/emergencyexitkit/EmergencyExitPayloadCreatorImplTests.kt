package build.wallet.emergencyexitkit

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.AppSpendingPrivateKeyMock
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.cloud.backup.csek.CsekDaoFake
import build.wallet.cloud.backup.csek.CsekFake
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.emergencyexitkit.EmergencyExitPayloadCreator.EmergencyExitPayloadCreatorError.AppPrivateKeyMissing
import build.wallet.emergencyexitkit.EmergencyExitPayloadCreator.EmergencyExitPayloadCreatorError.CsekMissing
import build.wallet.encrypt.SymmetricKeyEncryptorFake
import build.wallet.testing.shouldBeErr
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull

class EmergencyExitPayloadCreatorImplTests : FunSpec({
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

  afterTest {
    csekDao.reset()
    appPrivateKeyDao.reset()
    symmetricKeyEncryptor.reset()
  }

  test("Successful creation") {
    csekDao.set(SealedCsekFake, CsekFake)
    appPrivateKeyDao.storeAppSpendingKeyPair(
      AppSpendingKeypair(
        publicKey = AppSpendingPublicKeyMock,
        privateKey = AppSpendingPrivateKeyMock
      )
    )

    creator.create(
      keybox = KeyboxMock,
      sealedCsek = SealedCsekFake
    )
      .get()
      .shouldNotBeNull()
  }

  test("Creation fails when missing the unsealed Csek") {
    appPrivateKeyDao.storeAppSpendingKeyPair(
      AppSpendingKeypair(
        publicKey = AppSpendingPublicKeyMock,
        privateKey = AppSpendingPrivateKeyMock
      )
    )
    creator.create(
      keybox = KeyboxMock,
      sealedCsek = SealedCsekFake
    )
      .shouldBeErr(CsekMissing())
  }

  test("Creation fails when missing the app xprv") {
    csekDao.set(SealedCsekFake, CsekFake)
    creator.create(
      keybox = KeyboxMock,
      sealedCsek = SealedCsekFake
    )
      .shouldBeErr(AppPrivateKeyMissing())
  }
})
