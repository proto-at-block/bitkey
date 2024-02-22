package build.wallet.emergencyaccesskit

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.AppSpendingPrivateKeyMock
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.cloud.backup.csek.CsekDaoFake
import build.wallet.cloud.backup.csek.CsekFake
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.emergencyaccesskit.EmergencyAccessPayloadCreator.EmergencyAccessPayloadCreatorError.AppPrivateKeyMissing
import build.wallet.emergencyaccesskit.EmergencyAccessPayloadCreator.EmergencyAccessPayloadCreatorError.CsekMissing
import build.wallet.testing.shouldBeErr
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull

class EmergencyAccessPayloadCreatorImplTests : FunSpec({
  val csekDao = CsekDaoFake()
  val symmetricKeyEncryptor = SymmetricKeyEncryptorFake()
  val appPrivateKeyDao = AppPrivateKeyDaoFake()

  val creator =
    EmergencyAccessPayloadCreatorImpl(
      csekDao = csekDao,
      symmetricKeyEncryptor = symmetricKeyEncryptor,
      appPrivateKeyDao = appPrivateKeyDao
    )

  afterTest {
    csekDao.reset()
    appPrivateKeyDao.reset()
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
