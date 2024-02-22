package build.wallet.cloud.backup.v2

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.app.AppGlobalAuthKeypair
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.auth.AppGlobalAuthPrivateKeyMock
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.socrec.TrustedContactFake1
import build.wallet.bitkey.socrec.TrustedContactFake2
import build.wallet.bitkey.spending.AppSpendingPrivateKeyMock
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.cloud.backup.csek.CsekDaoFake
import build.wallet.cloud.backup.csek.CsekFake
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.cloud.backup.v2.FullAccountFieldsCreator.FullAccountFieldsCreationError.AppAuthPrivateKeyRetrievalError
import build.wallet.cloud.backup.v2.FullAccountFieldsCreator.FullAccountFieldsCreationError.AppSpendingPrivateKeyRetrievalError
import build.wallet.cloud.backup.v2.FullAccountFieldsCreator.FullAccountFieldsCreationError.PkekRetrievalError
import build.wallet.encrypt.SealedDataMock
import build.wallet.encrypt.SymmetricKeyEncryptorMock
import build.wallet.encrypt.XCiphertextMock
import build.wallet.recovery.socrec.SocRecCryptoFake
import build.wallet.recovery.socrec.SocRecKeysDaoFake
import build.wallet.recovery.socrec.SocRecKeysRepository
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull

class FullAccountFieldsCreatorImplTests : FunSpec({

  val symmetricKeyEncryptor = SymmetricKeyEncryptorMock()
  val appPrivateKeyDao = AppPrivateKeyDaoFake()
  val csekDao = CsekDaoFake()
  val trustedContacts = listOf(TrustedContactFake1, TrustedContactFake2)

  val fullAccountFieldsCreator =
    FullAccountFieldsCreatorImpl(
      appPrivateKeyDao = appPrivateKeyDao,
      csekDao = csekDao,
      symmetricKeyEncryptor = symmetricKeyEncryptor,
      socRecKeysRepository = SocRecKeysRepository(SocRecCryptoFake(), SocRecKeysDaoFake()),
      socRecCrypto = SocRecCryptoFake()
    )

  afterTest {
    csekDao.reset()
    appPrivateKeyDao.reset()
  }

  test("create full account backup") {
    csekDao.set(SealedCsekFake, CsekFake)
    appPrivateKeyDao.storeAppAuthKeyPair(
      AppGlobalAuthKeypair(
        publicKey = AppGlobalAuthPublicKeyMock,
        privateKey = AppGlobalAuthPrivateKeyMock
      )
    )
    appPrivateKeyDao.storeAppSpendingKeyPair(
      AppSpendingKeypair(
        publicKey = AppSpendingPublicKeyMock,
        privateKey = AppSpendingPrivateKeyMock
      )
    )
    symmetricKeyEncryptor.sealResult = SealedDataMock
    val backup =
      fullAccountFieldsCreator.create(
        keybox = KeyboxMock,
        sealedCsek = SealedCsekFake,
        trustedContacts = trustedContacts
      ).shouldBeOk()

    val backupWithoutSocRec =
      backup.copy(
        socRecEncryptionKeyCiphertextMap = mapOf(),
        socRecFullAccountKeysCiphertext = XCiphertextMock
      )
    backupWithoutSocRec.shouldBeEqual(
      FullAccountFieldsMock.copy(socRecEncryptionKeyCiphertextMap = mapOf())
    )

    backup.socRecEncryptionKeyCiphertextMap.shouldHaveSize(2)
  }

  test("create full account backup fails with PkekRetrievalError from exception") {
    val throwable = Throwable("foo")
    csekDao.getErrResult = Err(throwable)
    appPrivateKeyDao.storeAppAuthKeyPair(
      AppGlobalAuthKeypair(
        publicKey = AppGlobalAuthPublicKeyMock,
        privateKey = AppGlobalAuthPrivateKeyMock
      )
    )
    appPrivateKeyDao.storeAppSpendingKeyPair(
      AppSpendingKeypair(
        publicKey = AppSpendingPublicKeyMock,
        privateKey = AppSpendingPrivateKeyMock
      )
    )
    symmetricKeyEncryptor.sealResult = SealedDataMock
    val createResult =
      fullAccountFieldsCreator.create(
        keybox = KeyboxMock,
        sealedCsek = SealedCsekFake,
        trustedContacts = trustedContacts
      )
    createResult
      .shouldBeErrOfType<PkekRetrievalError>()
      .cause
      .shouldNotBeNull()
      .shouldBeEqual(throwable)
  }

  test("create full account backup fails with PkekRetrievalError from missing Pkek") {
    appPrivateKeyDao.storeAppAuthKeyPair(
      AppGlobalAuthKeypair(
        publicKey = AppGlobalAuthPublicKeyMock,
        privateKey = AppGlobalAuthPrivateKeyMock
      )
    )
    appPrivateKeyDao.storeAppSpendingKeyPair(
      AppSpendingKeypair(
        publicKey = AppSpendingPublicKeyMock,
        privateKey = AppSpendingPrivateKeyMock
      )
    )
    symmetricKeyEncryptor.sealResult = SealedDataMock
    val createResult =
      fullAccountFieldsCreator.create(
        keybox = KeyboxMock,
        sealedCsek = SealedCsekFake,
        trustedContacts = trustedContacts
      )
    createResult
      .shouldBeErrOfType<PkekRetrievalError>().cause.shouldBeNull()
  }

  test("create full account backup fails with PrivateKeyRetrievalError") {
    val throwable = Throwable("foo")
    appPrivateKeyDao.storeAppAuthKeyPair(
      AppGlobalAuthKeypair(
        publicKey = AppGlobalAuthPublicKeyMock,
        privateKey = AppGlobalAuthPrivateKeyMock
      )
    )
    appPrivateKeyDao.getAppSpendingPrivateKeyErrResult = Err(throwable)
    symmetricKeyEncryptor.sealResult = SealedDataMock
    val createResult =
      fullAccountFieldsCreator.create(
        keybox = KeyboxMock,
        sealedCsek = SealedCsekFake,
        trustedContacts = trustedContacts
      )
    createResult
      .shouldBeErrOfType<AppSpendingPrivateKeyRetrievalError>()
      .cause
      .shouldBeEqual(throwable)
  }

  test("create full account backup fails with AuthKeyRetrievalError") {
    val throwable = Throwable("foo")
    appPrivateKeyDao.getAppAuthPrivateKeyErrResult = Err(throwable)
    symmetricKeyEncryptor.sealResult = SealedDataMock
    val createResult =
      fullAccountFieldsCreator.create(
        keybox = KeyboxMock,
        sealedCsek = SealedCsekFake,
        trustedContacts = trustedContacts
      )
    createResult
      .shouldBeErrOfType<AppAuthPrivateKeyRetrievalError>()
      .cause
      .shouldBeEqual(throwable)
  }
})
