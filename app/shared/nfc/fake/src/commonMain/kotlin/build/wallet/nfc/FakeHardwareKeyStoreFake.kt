package build.wallet.nfc

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.spending.SpendingKeypair
import build.wallet.bitkey.spending.SpendingPrivateKey
import build.wallet.bitkey.spending.SpendingPublicKey
import build.wallet.encrypt.Secp256k1PrivateKey
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.platform.random.uuid
import okio.ByteString.Companion.encodeUtf8

class FakeHardwareKeyStoreFake : FakeHardwareKeyStore {
  override suspend fun getSeed(): FakeHardwareKeyStore.Seed {
    TODO("Not yet implemented")
  }

  override suspend fun setSeed(words: FakeHardwareKeyStore.Seed) {
    TODO("Not yet implemented")
  }

  private var authKeypair: FakeHwAuthKeypair? = null

  override suspend fun getAuthKeypair(): FakeHwAuthKeypair {
    return authKeypair ?: run {
      val authKeySeed = uuid()
      FakeHwAuthKeypair(
        publicKey = HwAuthPublicKey(pubKey = Secp256k1PublicKey(value = "hwAuthPublic-$authKeySeed")),
        privateKey = FakeHwAuthPrivateKey(key = Secp256k1PrivateKey(bytes = "hwAuthPrivate-$authKeySeed".encodeUtf8()))
      ).also {
        authKeypair = it
      }
    }
  }

  override suspend fun getInitialSpendingKeypair(network: BitcoinNetworkType): SpendingKeypair {
    TODO("Not yet implemented")
  }

  override suspend fun getNextSpendingKeypair(
    existingDescriptorPublicKeys: List<String>,
    network: BitcoinNetworkType,
  ): SpendingKeypair {
    TODO("Not yet implemented")
  }

  override suspend fun getSpendingPrivateKey(
    pubKey: SpendingPublicKey,
    network: BitcoinNetworkType,
  ): SpendingPrivateKey {
    TODO("Not yet implemented")
  }

  override suspend fun clear() {
    authKeypair = null
  }
}
