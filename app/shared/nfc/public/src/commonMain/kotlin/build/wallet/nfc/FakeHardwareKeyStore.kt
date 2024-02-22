package build.wallet.nfc

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.bitkey.spending.SpendingKeypair
import build.wallet.bitkey.spending.SpendingPrivateKey
import build.wallet.bitkey.spending.SpendingPublicKey
import dev.zacsweers.redacted.annotations.Redacted

interface FakeHardwareKeyStore {
  suspend fun getSeed(): String

  suspend fun setSeed(words: String)

  suspend fun getAuthKeypair(): FakeHwAuthKeypair

  suspend fun getInitialSpendingKeypair(network: BitcoinNetworkType): SpendingKeypair

  suspend fun getNextSpendingKeypair(
    existingDescriptorPublicKeys: List<String>,
    network: BitcoinNetworkType,
  ): SpendingKeypair

  /**
   * Returns the private key matching the given public key.
   *
   * @param pubKey The public key from which to derive a private key.
   * @param network The bitcoin network the key is on.
   */
  suspend fun getSpendingPrivateKey(
    pubKey: SpendingPublicKey,
    network: BitcoinNetworkType,
  ): SpendingPrivateKey

  suspend fun clear()

  @Redacted
  data class FakeHwSpendingPrivateKey(
    override val key: ExtendedPrivateKey,
  ) : SpendingPrivateKey
}
