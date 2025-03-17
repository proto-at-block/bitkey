package build.wallet.nfc

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilder
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitcoin.wallet.SpendingWalletDescriptor
import build.wallet.bitcoin.wallet.SpendingWalletProvider
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.bitkey.spending.SpendingPrivateKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.getOrThrow

/**
 * Provider for fake hardware [SpendingWallet].
 * Descriptor for this wallet uses public app and f8e spending keys and private fake hardware
 * spending key from [FakeHardwareKeyStore].
 */
@BitkeyInject(AppScope::class)
class FakeHardwareSpendingWalletProvider(
  private val spendingWalletProvider: SpendingWalletProvider,
  private val descriptorBuilder: BitcoinMultiSigDescriptorBuilder,
  private val fakeHardwareKeyStore: FakeHardwareKeyStore,
) {
  /**
   * Create a [SpendingWallet] for the given [SpendingKeyset]. Actual fake hardware
   * private key from fake hardware key store will be used to create wallet descriptor.
   */
  suspend fun get(spendingKeyset: SpendingKeyset): SpendingWallet {
    return spendingWalletProvider.getWallet(walletDescriptor(spendingKeyset)).getOrThrow()
  }

  private suspend fun walletDescriptor(keyset: SpendingKeyset): SpendingWalletDescriptor {
    val fakeHwSpendingPrivateKey = readHwPrivateSpendingKey(keyset.hardwareKey, keyset.networkType)

    // We swap the app private key with the fake hardware private key. Practically speaking
    // this does not change the descriptor, but it does allow us to use the fake hardware
    // private key to sign transactions.
    val receivingDescriptor =
      descriptorBuilder.spendingReceivingDescriptor(
        appPrivateKey = fakeHwSpendingPrivateKey.key,
        hardwareKey = keyset.appKey.key,
        serverKey = keyset.f8eSpendingKeyset.spendingPublicKey.key
      )
    val changeDescriptor =
      descriptorBuilder.spendingChangeDescriptor(
        appPrivateKey = fakeHwSpendingPrivateKey.key,
        hardwareKey = keyset.appKey.key,
        serverKey = keyset.f8eSpendingKeyset.spendingPublicKey.key
      )

    return SpendingWalletDescriptor(
      // We prefix the ID with "fake-kw" to make sure a new db is created
      // for this tweaked keyset. Otherwise BDK will fail its descriptor checksum
      identifier = "fake-hw-${keyset.localId}",
      networkType = keyset.networkType,
      receivingDescriptor = receivingDescriptor,
      changeDescriptor = changeDescriptor
    )
  }

  private suspend fun readHwPrivateSpendingKey(
    publicKey: HwSpendingPublicKey,
    network: BitcoinNetworkType,
  ): SpendingPrivateKey = fakeHardwareKeyStore.getSpendingPrivateKey(publicKey, network)
}
