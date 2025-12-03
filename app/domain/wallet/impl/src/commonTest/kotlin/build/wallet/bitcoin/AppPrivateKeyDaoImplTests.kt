package build.wallet.bitcoin

import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.auth.*
import build.wallet.bitkey.spending.AppSpendingPrivateKeyMock
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import build.wallet.store.EncryptedKeyValueStoreFactoryFake
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.core.toByteArray
import okio.ByteString.Companion.toByteString

class AppPrivateKeyDaoImplTests : FunSpec({

  val encryptedKeyValueStoreFactory = EncryptedKeyValueStoreFactoryFake()
  val dao = AppPrivateKeyDaoImpl(encryptedKeyValueStoreFactory)

  beforeTest {
    encryptedKeyValueStoreFactory.reset()
  }

  val spendingPublicKey = AppSpendingPublicKey(DescriptorPublicKeyMock(identifier = "xpub123"))
  val spendingPrivateKey =
    AppSpendingPrivateKey(ExtendedPrivateKey(xprv = "xprv123", mnemonic = "mnemonic123"))

  context("spending key") {
    test("stores private key using public key") {
      val key = AppSpendingKeypair(spendingPublicKey, spendingPrivateKey)

      dao.getAppSpendingPrivateKey(spendingPublicKey).shouldBe(Ok(null))
      dao.storeAppSpendingKeyPair(key).shouldBeOk()
      dao.getAppSpendingPrivateKey(spendingPublicKey).shouldBe(Ok(spendingPrivateKey))
    }

    test("private key and mnemonic are not present") {
      dao.getAppSpendingPrivateKey(spendingPublicKey).shouldBe(Ok(null))
    }

    test("private key and mnemonic are present") {
      encryptedKeyValueStoreFactory.store.putString(
        key = "secret-key:${spendingPublicKey.key.dpub}",
        value = spendingPrivateKey.key.xprv
      )
      encryptedKeyValueStoreFactory.store.putString(
        key = "mnemonic:${spendingPublicKey.key.dpub}",
        value = spendingPrivateKey.key.mnemonic
      )

      dao.getAppSpendingPrivateKey(spendingPublicKey).shouldBe(Ok(spendingPrivateKey))
    }

    test("private key is present but mnemonic is missing") {
      // Add private key but not mnemonic
      encryptedKeyValueStoreFactory.store.putString(
        key = "secret-key:${spendingPublicKey.key.dpub}",
        value = spendingPrivateKey.key.xprv
      )

      dao.getAppSpendingPrivateKey(spendingPublicKey).shouldBeErrOfType<MnemonicMissingError>()
    }

    test("mnemonic is present but private key is missing") {
      // Add mnemonic but not private key
      encryptedKeyValueStoreFactory.store.putString(
        key = "mnemonic:${spendingPublicKey.key.dpub}",
        value = spendingPrivateKey.key.mnemonic
      )

      dao.getAppSpendingPrivateKey(spendingPublicKey)
        .shouldBeErrOfType<PrivateKeyMissingError>()
    }
  }

  context("recovery auth key") {
    test("private key is not present") {
      dao.getAsymmetricPrivateKey(AppRecoveryAuthPublicKeyMock)
        .shouldBeOk(null)
    }

    test("private key is found") {
      encryptedKeyValueStoreFactory.store.putString(
        key = AppRecoveryAuthPublicKeyMock.value,
        value = AppRecoveryAuthPrivateKeyMock.bytes.hex()
      )

      dao.getAsymmetricPrivateKey(AppRecoveryAuthPublicKeyMock)
        .shouldBeOk(AppRecoveryAuthPrivateKeyMock)
    }

    test("store keys") {
      dao.storeAppKeyPair(AppRecoveryAuthKeypairMock).shouldBeOk()

      dao.getAsymmetricPrivateKey(AppRecoveryAuthPublicKeyMock)
        .shouldBeOk(AppRecoveryAuthPrivateKeyMock)
    }
  }

  context("global auth key") {
    test("private key is not present") {
      dao.getAsymmetricPrivateKey(AppGlobalAuthPublicKeyMock)
        .shouldBeOk(null)
    }

    test("private key is found") {
      encryptedKeyValueStoreFactory.store.putString(
        key = AppGlobalAuthPublicKeyMock.value,
        value = AppGlobalAuthPrivateKeyMock.bytes.hex()
      )

      dao.getAsymmetricPrivateKey(AppGlobalAuthPublicKeyMock)
        .shouldBeOk(AppGlobalAuthPrivateKeyMock)
    }

    test("store keys") {
      dao.storeAppKeyPair(AppGlobalAuthKeypairMock).shouldBeOk()

      dao.getAsymmetricPrivateKey(AppGlobalAuthPublicKeyMock)
        .shouldBeOk(AppGlobalAuthPrivateKeyMock)
    }
  }

  context("asymmetric key") {
    test("private key is not present") {
      dao.getAsymmetricPrivateKey(PublicKey<Nothing>("pubkey"))
        .shouldBeOk(null)
    }

    test("private key is found") {
      val publicKey = PublicKey<Nothing>("pubkey")
      val privateKey = PrivateKey<Nothing>("privkey".toByteArray().toByteString())
      encryptedKeyValueStoreFactory.store.putString(
        key = publicKey.value,
        value = privateKey.bytes.hex()
      )

      dao.getAsymmetricPrivateKey(publicKey)
        .shouldBeOk(privateKey)
    }

    test("store keys") {
      val publicKey = PublicKey<Nothing>("pubkey")
      val privateKey = PrivateKey<Nothing>("privkey".toByteArray().toByteString())
      dao.storeAsymmetricPrivateKey(publicKey, privateKey).shouldBeOk()

      dao.getAsymmetricPrivateKey(publicKey)
        .shouldBeOk(privateKey)
    }
  }

  test("clear operation") {
    val keystore = AppPrivateKeyDaoImpl(encryptedKeyValueStoreFactory)

    val spendingKeypair =
      AppSpendingKeypair(AppSpendingPublicKeyMock, AppSpendingPrivateKeyMock)
    keystore.storeAppSpendingKeyPair(spendingKeypair).shouldBeOk()
    val globalAuthKeypair = AppGlobalAuthKeypairMock
    keystore.storeAppKeyPair(globalAuthKeypair).shouldBeOk()
    val recoveryAuthKeypair = AppRecoveryAuthKeypairMock
    keystore.storeAppKeyPair(recoveryAuthKeypair).shouldBeOk()

    keystore.clear().shouldBeOk()

    keystore.getAppSpendingPrivateKey(AppSpendingPublicKeyMock).shouldBeOk(null)
    keystore.getAsymmetricPrivateKey(AppGlobalAuthPublicKeyMock).shouldBeOk(null)
    keystore.getAsymmetricPrivateKey(AppRecoveryAuthPublicKeyMock).shouldBeOk(null)
  }

  test("getAllAppSpendingKeyPairs returns empty list when no keys stored") {
    dao.getAllAppSpendingKeyPairs().shouldBeOk(emptyList())
  }

  test("getAllAppSpendingKeyPairs returns single key pair") {
    val keypair = AppSpendingKeypair(spendingPublicKey, spendingPrivateKey)
    dao.storeAppSpendingKeyPair(keypair).shouldBeOk()

    val result = dao.getAllAppSpendingKeyPairs().shouldBeOk()
    result.size.shouldBe(1)
    result[0].publicKey.shouldBe(spendingPublicKey)
    result[0].privateKey.shouldBe(spendingPrivateKey)
  }

  test("getAllAppSpendingKeyPairs returns multiple key pairs") {
    val keypair1 = AppSpendingKeypair(spendingPublicKey, spendingPrivateKey)
    val publicKey2 = AppSpendingPublicKey(DescriptorPublicKeyMock(identifier = "xpub456"))
    val privateKey2 =
      AppSpendingPrivateKey(ExtendedPrivateKey(xprv = "xprv456", mnemonic = "mnemonic456"))
    val keypair2 = AppSpendingKeypair(publicKey2, privateKey2)

    dao.storeAppSpendingKeyPair(keypair1).shouldBeOk()
    dao.storeAppSpendingKeyPair(keypair2).shouldBeOk()

    val result = dao.getAllAppSpendingKeyPairs().shouldBeOk()
    result.size.shouldBe(2)

    // Verify both key pairs are present (order may vary)
    val publicKeys = result.map { it.publicKey }.toSet()
    publicKeys.shouldBe(setOf(spendingPublicKey, publicKey2))
  }

  test("getAllAppSpendingKeyPairs ignores keys with missing mnemonic") {
    val validKeypair = AppSpendingKeypair(spendingPublicKey, spendingPrivateKey)
    dao.storeAppSpendingKeyPair(validKeypair).shouldBeOk()

    // Add a key with missing mnemonic
    val invalidPublicKey = AppSpendingPublicKey(DescriptorPublicKeyMock(identifier = "invalid"))
    encryptedKeyValueStoreFactory.store.putString(
      key = "secret-key:${invalidPublicKey.key.dpub}",
      value = "xprv-invalid"
    )

    val result = dao.getAllAppSpendingKeyPairs().shouldBeOk()
    result.size.shouldBe(1)
    result[0].publicKey.shouldBe(spendingPublicKey)
  }
})
