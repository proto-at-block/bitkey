package build.wallet.bitcoin.treasury

import build.wallet.bdk.bindings.BdkDescriptorFactory
import build.wallet.bdk.bindings.BdkDescriptorSecretKeyFactory
import build.wallet.bdk.bindings.BdkKeychainKind.EXTERNAL
import build.wallet.bdk.bindings.BdkKeychainKind.INTERNAL
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.BitcoinNetworkType.REGTEST
import build.wallet.bitcoin.bdk.bdkNetwork
import build.wallet.bitcoin.blockchain.BitcoinBlockchain
import build.wallet.bitcoin.blockchain.BlockchainControl
import build.wallet.bitcoin.descriptor.BitcoinDescriptor.Spending
import build.wallet.bitcoin.treasury.secrets.loadTreasuryWalletPrivateKey
import build.wallet.bitcoin.wallet.SpendingWalletDescriptor
import build.wallet.bitcoin.wallet.SpendingWalletProvider
import com.github.michaelbull.result.getOrThrow

/**
 * Creates a [TreasuryWallet] with the signing keys for a Signet treasury that we manually fund.
 *
 * The private key may be loaded from AWS Secrets Manager from a default location, or provided
 * as an environment variable.
 */
class TreasuryWalletFactory(
  private val bitcoinBlockchain: BitcoinBlockchain,
  private val blockchainControl: BlockchainControl,
  private val spendingWalletProvider: SpendingWalletProvider,
  private val bdkDescriptorSecretKeyFactory: BdkDescriptorSecretKeyFactory,
  private val bdkDescriptorFactory: BdkDescriptorFactory,
) {
  suspend fun create(networkType: BitcoinNetworkType): TreasuryWallet {
    val descriptorSecretKey =
      if (networkType == REGTEST) {
        // For Regtest, we fund a static private key shared between the bitcoind node and the tests.
        "tprv8h8PWPocKYoPkajXdGQhTwnqb9sSBiT6vGif5zJongZoAXKmWxkTcqZpRPNmtzzFojgN4k7DFdeMUY2cHFQCwEyQRyejXcs2RKjnbZTPMj3"
      } else {
        // For other networks, we use a manually funded shared Treasury.
        // Currently, it is only funded on Signet
        loadTreasuryWalletPrivateKey()
      }
    val key = bdkDescriptorSecretKeyFactory.fromString(descriptorSecretKey)

    val walletDescriptor =
      SpendingWalletDescriptor(
        identifier = "treasury-wallet",
        networkType = networkType,
        receivingDescriptor =
          Spending(
            bdkDescriptorFactory
              .bip84(key, EXTERNAL, networkType.bdkNetwork)
              .asStringPrivate()
          ),
        changeDescriptor =
          Spending(
            bdkDescriptorFactory
              .bip84(key, INTERNAL, networkType.bdkNetwork)
              .asStringPrivate()
          )
      )

    val spendingWallet =
      spendingWalletProvider.getWallet(
        walletDescriptor
      ).getOrThrow()

    return TreasuryWallet(bitcoinBlockchain, blockchainControl, spendingWallet)
  }
}
