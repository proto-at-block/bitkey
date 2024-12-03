package build.wallet.nfc

import build.wallet.bdk.BdkDescriptorSecretKeyGeneratorImpl
import build.wallet.bdk.BdkMnemonicGeneratorImpl
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitkey.spending.SpendingKeypair
import build.wallet.encrypt.Secp256k1KeyGeneratorImpl
import build.wallet.store.EncryptedKeyValueStoreFactoryFake
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldHaveLength
import kotlinx.collections.immutable.toImmutableList

const val OTHER_KEY_1: String = "[deadbeef/84'/1'/0']tpub6ERApfZwUNrhLCkDtcHTcxd75RbzS1ed54G1LkBUHQVHQKqhMkhgbmJbZRkrgZw4koxb5JaHWkY4ALHY2grBGRjaDMzQLcgJvLJuZZvRcEL/*"
const val OTHER_KEY_2: String = "[deadbeef/84'/1'/1']tpubDDPPGTZQATrQxRuHj3N8BveJhsQiUWmqhLngWnbsBqkTN4jxfRxn7RUE1T2pMREaJwRFo1jQJ7RH6FZT8u1L32EUudaEAdvh8DvuK3pfvWN/*"
const val OTHER_KEY_3: String = "[deadbeef/84'/1'/2']tpubDDRaMQwyFhktiNHd1HwmkaBu6xd5itCbf6sKxQDGHNix5ntsLhCBqHwQtDpMJ6t1Qhg6kdUww1VqUfEJYMTPsHFtebiWxoDVytpY2cL5jZJ/*"

// TODO(W-3913) This test should be in commonJvmTest
class FakeHardwareKeyStoreTest : FunSpec({
  val encryptedKeyValueStoreFactory = EncryptedKeyValueStoreFactoryFake()
  val fakeHardwareKeyStore =
    FakeHardwareKeyStoreImpl(
      BdkMnemonicGeneratorImpl(),
      BdkDescriptorSecretKeyGeneratorImpl(),
      Secp256k1KeyGeneratorImpl(),
      encryptedKeyValueStoreFactory
    )

  beforeTest {
    encryptedKeyValueStoreFactory.reset()
  }

  test("get auth key returns keys in the right format") {
    val authKeys = fakeHardwareKeyStore.getAuthKeypair()

    // Compressed public key should start with 02 and be 33 bytes
    // https://en.bitcoin.it/wiki/BIP_0137
    authKeys.publicKey.pubKey.value.run {
      shouldHaveLength(66)
      val hasRightPrefix = this.startsWith("02") || this.startsWith("03")
      hasRightPrefix.shouldBeTrue()
    }
    // Private key should be 256 bits or 32 bytes
    authKeys.privateKey.key.bytes.toByteArray().size
      .shouldBe(32)
  }

  test("get auth key returns the same key") {
    val authKeys = fakeHardwareKeyStore.getAuthKeypair()
    val newAuthKeys = fakeHardwareKeyStore.getAuthKeypair()

    authKeys.shouldBeEqual(newAuthKeys)
  }

  test("get initial spending key returns keys in the right format") {
    val spendingKey = fakeHardwareKeyStore.getInitialSpendingKeypair(SIGNET)

    // This is a descriptor private key with origin information...
    // should it actually only have the xprv part?
    spendingKey.privateKey.key.xprv.shouldContain("tprv")
  }

  test("get initial spending key returns the same key") {
    val spendingKey = fakeHardwareKeyStore.getInitialSpendingKeypair(SIGNET)
    val newSpendingKey = fakeHardwareKeyStore.getInitialSpendingKeypair(SIGNET)

    spendingKey.shouldBeEqual(newSpendingKey)
  }

  test("clear will allow new keys to get generated") {
    val authKeys = fakeHardwareKeyStore.getAuthKeypair()
    val spendingKey = fakeHardwareKeyStore.getInitialSpendingKeypair(SIGNET)

    fakeHardwareKeyStore.clear()

    val newAuthKeys = fakeHardwareKeyStore.getAuthKeypair()
    val newSpendingKey = fakeHardwareKeyStore.getInitialSpendingKeypair(SIGNET)

    authKeys.shouldNotBeEqual(newAuthKeys)
    spendingKey.shouldNotBeEqual(newSpendingKey)
  }

  test("get next spending keypair with no existing keys returns initial keypair") {
    val spendingKey = fakeHardwareKeyStore.getInitialSpendingKeypair(SIGNET)
    val nextKey = fakeHardwareKeyStore.getNextSpendingKeypair(listOf(), SIGNET)
    nextKey.shouldBeEqual(spendingKey)
  }

  test("get next spending keypair with non-matching existing keys returns initial keypair") {
    val spendingKey = fakeHardwareKeyStore.getInitialSpendingKeypair(SIGNET)
    val nextKey =
      fakeHardwareKeyStore.getNextSpendingKeypair(
        listOf(
          OTHER_KEY_1,
          OTHER_KEY_2,
          OTHER_KEY_3
        ),
        SIGNET
      )
    nextKey.shouldBeEqual(spendingKey)
  }

  test("get next spending keypair with initial key returns new keypair") {
    val spendingKey = fakeHardwareKeyStore.getInitialSpendingKeypair(SIGNET)
    val nextKey =
      fakeHardwareKeyStore.getNextSpendingKeypair(
        listOf(
          spendingKey.publicKey.key.dpub
        ),
        SIGNET
      )
    nextKey.shouldNotBeEqual(spendingKey)
    nextKey.publicKey.key.origin.derivationPath.shouldEndWith("1'")
  }

  suspend fun genSequentialNextKeys(
    keyStore: FakeHardwareKeyStore,
    networkType: BitcoinNetworkType,
    count: Int,
  ): List<SpendingKeypair> {
    require(count > 0)
    val keys = mutableListOf(keyStore.getInitialSpendingKeypair(networkType))
    for (i in 1 until count) {
      keys.add(keyStore.getNextSpendingKeypair(keys.map { it.publicKey.key.dpub }, networkType))
    }
    return keys.toImmutableList()
  }

  test("get next spending keypair generate new ordered keys") {
    val spendingKey = fakeHardwareKeyStore.getInitialSpendingKeypair(SIGNET)
    val keys = genSequentialNextKeys(fakeHardwareKeyStore, SIGNET, 3)

    keys[0].shouldBeEqual(spendingKey)
    for (i in 1 until 3) {
      withClue("key $i") {
        val dpub = keys[i].publicKey.key
        dpub.origin.derivationPath.shouldEndWith("$i'")
        dpub.xpub.shouldNotBeEqual(keys[0].publicKey.key.xpub)
      }
    }
  }

  test("get next spending keypair picks max matching key") {
    val keys = genSequentialNextKeys(fakeHardwareKeyStore, SIGNET, 3)

    val next =
      fakeHardwareKeyStore.getNextSpendingKeypair(
        listOf(
          OTHER_KEY_1,
          OTHER_KEY_2,
          keys[1].publicKey.key.dpub,
          OTHER_KEY_3,
          keys[0].publicKey.key.dpub
        ),
        SIGNET
      )
    next.shouldBeEqual(keys[2])
  }

  test("get next spending keypair is tolerant of skipped indexes") {
    val keys = genSequentialNextKeys(fakeHardwareKeyStore, SIGNET, 6)

    val next =
      fakeHardwareKeyStore.getNextSpendingKeypair(
        listOf(
          OTHER_KEY_1,
          keys[5].publicKey.key.dpub,
          OTHER_KEY_2,
          OTHER_KEY_3,
          keys[0].publicKey.key.dpub
        ),
        SIGNET
      )

    val expect = genSequentialNextKeys(fakeHardwareKeyStore, SIGNET, 7)[6]
    next.shouldBeEqual(expect)
  }

  test("getSpendingPrivateKey") {
    val keys = genSequentialNextKeys(fakeHardwareKeyStore, SIGNET, 6)

    val subject = keys[3]
    fakeHardwareKeyStore.getSpendingPrivateKey(subject.publicKey, SIGNET)
      .shouldBeEqual(subject.privateKey)
  }
})
