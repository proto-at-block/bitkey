package build.wallet.bitcoin

import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.auth.AppGlobalAuthKeypairMock
import build.wallet.bitkey.auth.AppGlobalAuthPrivateKeyMock
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.auth.AppRecoveryAuthKeypairMock
import build.wallet.bitkey.auth.AppRecoveryAuthPrivateKeyMock
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
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
      dao.getRecoveryAuthKey(AppRecoveryAuthPublicKeyMock)
        .shouldBeOk(null)
    }

    test("private key is found") {
      encryptedKeyValueStoreFactory.store.putString(
        key = AppRecoveryAuthPublicKeyMock.pubKey.value,
        value = AppRecoveryAuthPrivateKeyMock.key.bytes.hex()
      )

      dao.getRecoveryAuthKey(AppRecoveryAuthPublicKeyMock)
        .shouldBeOk(AppRecoveryAuthPrivateKeyMock)
    }

    test("store keys") {
      dao.storeAppAuthKeyPair(AppRecoveryAuthKeypairMock).shouldBeOk()

      dao.getRecoveryAuthKey(AppRecoveryAuthPublicKeyMock)
        .shouldBeOk(AppRecoveryAuthPrivateKeyMock)
    }
  }

  context("global auth key") {
    test("private key is not present") {
      dao.getGlobalAuthKey(AppGlobalAuthPublicKeyMock)
        .shouldBeOk(null)
    }

    test("private key is found") {
      encryptedKeyValueStoreFactory.store.putString(
        key = AppGlobalAuthPublicKeyMock.pubKey.value,
        value = AppGlobalAuthPrivateKeyMock.key.bytes.hex()
      )

      dao.getGlobalAuthKey(AppGlobalAuthPublicKeyMock)
        .shouldBeOk(AppGlobalAuthPrivateKeyMock)
    }

    test("store keys") {
      dao.storeAppAuthKeyPair(AppGlobalAuthKeypairMock).shouldBeOk()

      dao.getGlobalAuthKey(AppGlobalAuthPublicKeyMock)
        .shouldBeOk(AppGlobalAuthPrivateKeyMock)
    }
  }

  context("asymmetric key") {
    test("private key is not present") {
      dao.getAsymmetricPrivateKey(PublicKey("pubkey"))
        .shouldBeOk(null)
    }

    test("private key is found") {
      val publicKey = PublicKey("pubkey")
      val privateKey = PrivateKey("privkey".toByteArray().toByteString())
      encryptedKeyValueStoreFactory.store.putString(
        key = publicKey.value,
        value = privateKey.bytes.hex()
      )

      dao.getAsymmetricPrivateKey(publicKey)
        .shouldBeOk(privateKey)
    }

    test("store keys") {
      val publicKey = PublicKey("pubkey")
      val privateKey = PrivateKey("privkey".toByteArray().toByteString())
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
    keystore.storeAppAuthKeyPair(globalAuthKeypair).shouldBeOk()
    val recoveryAuthKeypair = AppRecoveryAuthKeypairMock
    keystore.storeAppAuthKeyPair(recoveryAuthKeypair).shouldBeOk()

    keystore.clear().shouldBeOk()

    keystore.getAppSpendingPrivateKey(AppSpendingPublicKeyMock).shouldBeOk(null)
    keystore.getGlobalAuthKey(AppGlobalAuthPublicKeyMock).shouldBeOk(null)
    keystore.getRecoveryAuthKey(AppRecoveryAuthPublicKeyMock).shouldBeOk(null)
  }
})
