package build.wallet.nfc

import build.wallet.bdk.bindings.BdkDerivationPath
import build.wallet.bdk.bindings.BdkDescriptorSecretKey
import build.wallet.bdk.bindings.BdkDescriptorSecretKeyGenerator
import build.wallet.bdk.bindings.BdkMnemonicGenerator
import build.wallet.bdk.bindings.BdkMnemonicWordCount.WORDS_24
import build.wallet.bdk.bindings.generateMnemonic
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.bdk.bdkNetwork
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeypair
import build.wallet.bitkey.spending.SpendingPrivateKey
import build.wallet.bitkey.spending.SpendingPublicKey
import build.wallet.encrypt.Secp256k1KeyGenerator
import build.wallet.encrypt.Secp256k1PrivateKey
import build.wallet.nfc.FakeHardwareKeyStore.FakeHwSpendingPrivateKey
import build.wallet.store.EncryptedKeyValueStoreFactory
import build.wallet.store.getOrPutString
import com.github.michaelbull.result.getOrThrow
import okio.ByteString.Companion.toByteString

class FakeHardwareKeyStoreImpl(
  private val bdkMnemonicGenerator: BdkMnemonicGenerator,
  private val bdkDescriptorSecretKeyGenerator: BdkDescriptorSecretKeyGenerator,
  private val secp256k1KeyGenerator: Secp256k1KeyGenerator,
  private val encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
) : FakeHardwareKeyStore {
  private suspend fun store() = encryptedKeyValueStoreFactory.getOrCreate(STORE_NAME)

  override suspend fun getSeed(): FakeHardwareKeyStore.Seed {
    val seed = store().getOrPutString(SEED_KEY) {
      bdkMnemonicGenerator.generateMnemonic(WORDS_24).words
    }
    return FakeHardwareKeyStore.Seed(seed)
  }

  override suspend fun setSeed(seed: FakeHardwareKeyStore.Seed) {
    return store().putString(SEED_KEY, seed.words)
  }

  private suspend fun getRootPrivateKey(network: BitcoinNetworkType): BdkDescriptorSecretKey {
    return bdkDescriptorSecretKeyGenerator
      .generate(
        network = network.bdkNetwork,
        mnemonic = bdkMnemonicGenerator.fromString(getSeed().words)
      )
  }

  override suspend fun getAuthKeypair(): FakeHwAuthKeypair {
    // Network does not matter for auth key
    val key =
      getRootPrivateKey(SIGNET)
        .derive(BdkDerivationPath("m/87497287'/0'"))
        .result.getOrThrow()
    val secretBytes = Secp256k1PrivateKey(key.secretBytes().toByteString())
    val pubKey = secp256k1KeyGenerator.derivePublicKey(secretBytes)
    return FakeHwAuthKeypair(
      publicKey = HwAuthPublicKey(pubKey),
      privateKey = FakeHwAuthPrivateKey(Secp256k1PrivateKey(secretBytes.bytes))
    )
  }

  override suspend fun getInitialSpendingKeypair(network: BitcoinNetworkType) =
    generateSpendingKeypair(network, 0)

  override suspend fun getNextSpendingKeypair(
    existingDescriptorPublicKeys: List<String>,
    network: BitcoinNetworkType,
  ): SpendingKeypair {
    val initial = getInitialSpendingKeypair(network)
    val initialDpub = initial.publicKey.key
    val expectPath = extractPathParts(initialDpub.origin.derivationPath)!!
    val maxAccount =
      existingDescriptorPublicKeys.map(DescriptorPublicKey::invoke)
        .filter { initialDpub.origin.fingerprint == it.origin.fingerprint }
        .mapNotNull { extractPathParts(it.origin.derivationPath) }
        .filter { expectPath.coinType == it.coinType }
        .maxOfOrNull { it.account }
    return if (maxAccount == null) {
      initial
    } else {
      generateSpendingKeypair(network, maxAccount + 1)
    }
  }

  override suspend fun getSpendingPrivateKey(
    pubKey: SpendingPublicKey,
    network: BitcoinNetworkType,
  ): SpendingPrivateKey {
    val derivationPath = "m${pubKey.key.origin.derivationPath}"
    val derivedKey =
      getRootPrivateKey(network)
        .derive(BdkDerivationPath(derivationPath)).result
        .getOrThrow()
    return FakeHwSpendingPrivateKey(
      ExtendedPrivateKey(xprv = derivedKey.raw(), mnemonic = "no mnemonic on fake hardware")
    )
  }

  suspend fun generateSpendingKeypair(
    network: BitcoinNetworkType,
    index: Int,
  ): SpendingKeypair {
    val coinType = if (network == BITCOIN) "0" else "1"
    val derivationPath = "m/84'/$coinType'/$index'"
    val derivedKey =
      getRootPrivateKey(network)
        .derive(BdkDerivationPath(derivationPath)).result
        .getOrThrow()
    return SpendingKeypair(
      publicKey = HwSpendingPublicKey(derivedKey.asPublic().raw()),
      privateKey =
        FakeHwSpendingPrivateKey(
          ExtendedPrivateKey(xprv = derivedKey.raw(), mnemonic = "no mnemonic on fake hardware")
        )
    )
  }

  override suspend fun clear() {
    store().clear()
  }

  private companion object {
    // changing these values is a breaking change and will cause data loss
    // this class is only used for testing but will disrupt existing installations
    const val STORE_NAME = "fakeHardware"
    const val SEED_KEY = "seed-key"

    val pathPattern = Regex("""^/84'/(\d+)'/(\d+)'""")

    fun extractPathParts(derivationPath: String): PathParts? {
      val match = pathPattern.matchEntire(derivationPath) ?: return null
      return PathParts(
        coinType = match.groupValues[1].toInt(),
        account = match.groupValues[2].toInt()
      )
    }
  }

  private data class PathParts(val coinType: Int, val account: Int)
}
