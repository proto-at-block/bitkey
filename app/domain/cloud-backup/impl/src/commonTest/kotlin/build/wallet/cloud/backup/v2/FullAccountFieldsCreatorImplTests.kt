package build.wallet.cloud.backup.v2

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.auth.AppGlobalAuthPrivateKeyMock
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.EndorsedTrustedContactFake1
import build.wallet.bitkey.relationships.EndorsedTrustedContactFake2
import build.wallet.bitkey.spending.AppSpendingPrivateKeyMock
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.bitkey.spending.PrivateSpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.cloud.backup.csek.CsekDaoFake
import build.wallet.cloud.backup.csek.CsekFake
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.cloud.backup.v2.FullAccountFieldsCreator.FullAccountFieldsCreationError.*
import build.wallet.encrypt.SealedDataMock
import build.wallet.encrypt.SymmetricKeyEncryptorFake
import build.wallet.encrypt.XCiphertextMock
import build.wallet.relationships.RelationshipsCryptoFake
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class FullAccountFieldsCreatorImplTests : FunSpec({

  val symmetricKeyEncryptor = SymmetricKeyEncryptorFake()
  val appPrivateKeyDao = AppPrivateKeyDaoFake()
  val csekDao = CsekDaoFake()
  val trustedContacts = listOf(EndorsedTrustedContactFake1, EndorsedTrustedContactFake2)

  val relationshipsCrypto = RelationshipsCryptoFake(appPrivateKeyDao = appPrivateKeyDao)
  val fullAccountFieldsCreator =
    FullAccountFieldsCreatorImpl(
      appPrivateKeyDao = appPrivateKeyDao,
      csekDao = csekDao,
      symmetricKeyEncryptor = symmetricKeyEncryptor,
      relationshipsCrypto = relationshipsCrypto
    )

  afterTest {
    csekDao.reset()
    appPrivateKeyDao.reset()
    symmetricKeyEncryptor.reset()
  }

  suspend fun prepareDaosWithFakes() {
    csekDao.set(SealedCsekFake, CsekFake)
    appPrivateKeyDao.storeAppKeyPair(
      AppKey(
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
  }

  test("keysets are included when canUseKeyboxKeysets is true") {
    prepareDaosWithFakes()

    // Create a keybox with canUseKeyboxKeysets = true and multiple keysets
    val keybox = KeyboxMock.copy(
      canUseKeyboxKeysets = true,
      keysets = listOf(SpendingKeysetMock, PrivateSpendingKeysetMock)
    )

    fullAccountFieldsCreator.create(
      keybox = keybox,
      sealedCsek = SealedCsekFake,
      endorsedTrustedContacts = trustedContacts
    ).shouldBeOk()

    // Get the sealed data from the fake encryptor and decode it
    val sealedJsonData = symmetricKeyEncryptor.lastSealedData.shouldNotBeNull()
    val fullAccountKeys = Json.decodeFromString<FullAccountKeys>(sealedJsonData.utf8())

    // Verify that keysets are included when canUseKeyboxKeysets is true
    fullAccountKeys.keysets.shouldBe(listOf(SpendingKeysetMock, PrivateSpendingKeysetMock))
  }

  test("keysets are empty when canUseKeyboxKeysets is false") {
    prepareDaosWithFakes()

    // Create a keybox with canUseKeyboxKeysets = false
    val keybox = KeyboxMock.copy(
      canUseKeyboxKeysets = false,
      keysets = listOf(SpendingKeysetMock, PrivateSpendingKeysetMock) // These should be ignored
    )

    fullAccountFieldsCreator.create(
      keybox = keybox,
      sealedCsek = SealedCsekFake,
      endorsedTrustedContacts = trustedContacts
    ).shouldBeOk()

    // Get the sealed data from the fake encryptor and decode it
    val sealedJsonData = symmetricKeyEncryptor.lastSealedData.shouldNotBeNull()
    val fullAccountKeys = Json.decodeFromString<FullAccountKeys>(sealedJsonData.utf8())

    // Verify that keysets are empty when canUseKeyboxKeysets is false
    fullAccountKeys.keysets.shouldBeEmpty()
  }

  test("create full account backup") {
    prepareDaosWithFakes()
    symmetricKeyEncryptor.sealNoMetadataResult = SealedDataMock
    val backup =
      fullAccountFieldsCreator.create(
        keybox = KeyboxMock,
        sealedCsek = SealedCsekFake,
        endorsedTrustedContacts = trustedContacts
      ).shouldBeOk()

    val backupWithoutSocRec =
      backup.copy(
        socRecSealedDekMap = mapOf(),
        socRecSealedFullAccountKeys = XCiphertextMock
      )
    backupWithoutSocRec.shouldBeEqual(
      FullAccountFieldsMock.copy(socRecSealedDekMap = mapOf())
    )

    backup.socRecSealedDekMap.shouldHaveSize(2)
  }

  test("create full account backup fails with PkekRetrievalError from exception") {
    val throwable = Throwable("foo")
    csekDao.getErrResult = Err(throwable)
    appPrivateKeyDao.storeAppKeyPair(
      AppKey(
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
    symmetricKeyEncryptor.sealNoMetadataResult = SealedDataMock
    val createResult =
      fullAccountFieldsCreator.create(
        keybox = KeyboxMock,
        sealedCsek = SealedCsekFake,
        endorsedTrustedContacts = trustedContacts
      )
    createResult
      .shouldBeErrOfType<PkekRetrievalError>()
      .cause
      .shouldNotBeNull()
      .shouldBeEqual(throwable)
  }

  test("create full account backup fails with PkekUnavailableError from missing Pkek") {
    appPrivateKeyDao.storeAppKeyPair(
      AppKey(
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
    symmetricKeyEncryptor.sealNoMetadataResult = SealedDataMock
    val createResult =
      fullAccountFieldsCreator.create(
        keybox = KeyboxMock,
        sealedCsek = SealedCsekFake,
        endorsedTrustedContacts = trustedContacts
      )
    createResult
      .shouldBeErrOfType<PkekUnavailableError>().cause.shouldBeNull()
  }

  test("create full account backup fails with PrivateKeyRetrievalError") {
    val throwable = Throwable("foo")
    appPrivateKeyDao.storeAppKeyPair(
      AppKey(
        publicKey = AppGlobalAuthPublicKeyMock,
        privateKey = AppGlobalAuthPrivateKeyMock
      )
    )
    appPrivateKeyDao.getAppSpendingPrivateKeyErrResult = Err(throwable)
    symmetricKeyEncryptor.sealNoMetadataResult = SealedDataMock
    val createResult =
      fullAccountFieldsCreator.create(
        keybox = KeyboxMock,
        sealedCsek = SealedCsekFake,
        endorsedTrustedContacts = trustedContacts
      )
    createResult
      .shouldBeErrOfType<AppSpendingPrivateKeyRetrievalError>()
      .cause
      .shouldBeEqual(throwable)
  }

  test("create full account backup fails with AuthKeyRetrievalError") {
    val throwable = Throwable("foo")
    appPrivateKeyDao.getAppPrivateKeyErrResult = Err(throwable)
    symmetricKeyEncryptor.sealNoMetadataResult = SealedDataMock
    val createResult =
      fullAccountFieldsCreator.create(
        keybox = KeyboxMock,
        sealedCsek = SealedCsekFake,
        endorsedTrustedContacts = trustedContacts
      )
    createResult
      .shouldBeErrOfType<AppAuthPrivateKeyRetrievalError>()
      .cause
      .shouldBeEqual(throwable)
  }
})
