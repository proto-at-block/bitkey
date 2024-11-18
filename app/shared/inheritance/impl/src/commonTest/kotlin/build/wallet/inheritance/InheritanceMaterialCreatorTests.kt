package build.wallet.inheritance

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.bitkey.relationships.EndorsedTrustedContactFake1
import build.wallet.bitkey.relationships.PrivateKeyEncryptionKey
import build.wallet.bitkey.spending.AppSpendingPrivateKeyMock
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.XCiphertext
import build.wallet.relationships.RelationshipsCrypto
import build.wallet.relationships.RelationshipsCryptoError
import build.wallet.relationships.RelationshipsCryptoFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

class InheritanceMaterialCreatorTests : FunSpec({
  val privateKeyDao = AppPrivateKeyDaoFake()
  val crypto = RelationshipsCryptoFake()
  val inheritanceRelationshipsProvider = InheritanceRelationshipsProviderFake()
  val creator = InheritanceCryptoImpl(
    appPrivateKeyDao = privateKeyDao,
    relationships = inheritanceRelationshipsProvider,
    crypto = crypto
  )

  test("Changing Spending Key should give a new hash") {
    inheritanceRelationshipsProvider.endorsedInheritanceContactsResult = listOf(EndorsedTrustedContactFake1)

    val result1 = creator.getInheritanceMaterialHashData(KeyboxMock)
    val result2 = creator.getInheritanceMaterialHashData(
      KeyboxMock.copy(
        activeAppKeyBundle = AppKeyBundleMock.copy(
          spendingKey = AppSpendingPublicKey(
            key = DescriptorPublicKeyMock(identifier = "test-modified-key")
          )
        )
      )
    )

    result1.isOk.shouldBeTrue()
    result2.isOk.shouldBeTrue()
    result1.shouldNotBeEqual(result2)
  }

  test("Changing Contacts should result in different hash") {
    inheritanceRelationshipsProvider.endorsedInheritanceContactsResult = listOf(EndorsedTrustedContactFake1)
    val result1 = creator.getInheritanceMaterialHashData(KeyboxMock)

    inheritanceRelationshipsProvider.endorsedInheritanceContactsResult = listOf(
      EndorsedTrustedContactFake1.copy(
        relationshipId = "test-modified-contact"
      )
    )
    val result2 = creator.getInheritanceMaterialHashData(KeyboxMock)

    result1.isOk.shouldBeTrue()
    result2.isOk.shouldBeTrue()
    result1.shouldNotBeEqual(result2)
  }

  test("Packages are encrypted for each contact") {
    privateKeyDao.appSpendingKeys[KeyboxMock.activeAppKeyBundle.spendingKey] = AppSpendingPrivateKeyMock
    val firstContact = EndorsedTrustedContactFake1.copy(
      relationshipId = "first-contact"
    )
    val secondContact = EndorsedTrustedContactFake1.copy(
      relationshipId = "second-contact"
    )
    inheritanceRelationshipsProvider.endorsedInheritanceContactsResult = listOf(firstContact, secondContact)

    val result = creator.createInheritanceMaterial(KeyboxMock)

    result.getOrThrow().packages.should { packages ->
      packages.size.shouldBe(2)
      packages[0].relationshipId.value.shouldBe("first-contact")
      packages[1].relationshipId.value.shouldBe("second-contact")
      // Sealed Encryption key should be encrypted per-contact:
      packages[0].sealedDek.shouldNotBeEqual(packages[1].sealedDek)
      // Mobile keys should all result in identical keys:
      packages[0].sealedMobileKey.shouldBeEqual(packages[1].sealedMobileKey)
    }
  }

  test("Creation fails with no spending keys") {
    privateKeyDao.appSpendingKeys.clear()
    inheritanceRelationshipsProvider.endorsedInheritanceContactsResult = listOf(EndorsedTrustedContactFake1)

    val result = creator.createInheritanceMaterial(KeyboxMock)

    result.isErr.shouldBeTrue()
  }

  test("Crypto Failure results in creation failure") {
    privateKeyDao.appSpendingKeys[KeyboxMock.activeAppKeyBundle.spendingKey] = AppSpendingPrivateKeyMock
    inheritanceRelationshipsProvider.endorsedInheritanceContactsResult = listOf(EndorsedTrustedContactFake1)
    val error = RelationshipsCryptoError.EncryptionFailed(Error("Test Failure"))
    val failingCrypto = object : RelationshipsCrypto by crypto {
      override fun encryptPrivateKeyEncryptionKey(
        delegatedDecryptionKey: PublicKey<DelegatedDecryptionKey>,
        privateKeyEncryptionKey: PrivateKeyEncryptionKey,
      ): Result<XCiphertext, RelationshipsCryptoError> {
        return Err(error)
      }
    }
    val testCreator = InheritanceCryptoImpl(
      appPrivateKeyDao = privateKeyDao,
      relationships = inheritanceRelationshipsProvider,
      crypto = failingCrypto
    )

    val result = testCreator.createInheritanceMaterial(KeyboxMock)

    result.isErr.shouldBeTrue()
    result.error.shouldBe(error)
  }
})
