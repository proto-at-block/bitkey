package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkWallet
import build.wallet.bdk.bindings.BdkWalletFactory
import build.wallet.bdk.bindings.wallet
import build.wallet.bitcoin.descriptor.BitcoinDescriptor
import build.wallet.bitcoin.wallet.WalletDescriptor
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.encodeUtf8

/**
 * Creates and caches [BdkWallet] instances. This class should be initialized
 * as a singleton in the DI graph.
 */

@BitkeyInject(AppScope::class)
class BdkWalletProviderImpl(
  private val bdkWalletFactory: BdkWalletFactory,
  private val bdkDatabaseConfigProvider: BdkDatabaseConfigProvider,
) : BdkWalletProvider {
  /**
   * Using suspending lock to ensure that we maintain a single [BdkWallet] instance
   * per descriptor set. This is to avoid BDK race conditions: https://github.com/bitcoindevkit/bdk/issues/915.
   */
  private val bdkLock = Mutex(locked = false)
  private val wallets = mutableMapOf<String, BdkWallet>()

  override suspend fun getBdkWallet(
    walletDescriptor: WalletDescriptor,
  ): Result<BdkWallet, BdkError> =
    coroutineBinding {
      bdkLock.withLock {
        val key = hashKeyForWallet(walletDescriptor)
        wallets.getOrPut(key) {
          createBdkWallet(walletDescriptor).bind()
        }
      }
    }

  /**
   * Creates a new instance of [BdkWallet] for given descriptors.
   */
  private suspend fun createBdkWallet(
    walletDescriptor: WalletDescriptor,
  ): Result<BdkWallet, BdkError> {
    val sqliteConfig = bdkDatabaseConfigProvider.sqliteConfig(walletDescriptor.identifier)
    return bdkWalletFactory
      .wallet(
        descriptor = walletDescriptor.receivingDescriptor.raw,
        changeDescriptor = walletDescriptor.changeDescriptor.raw,
        network = walletDescriptor.networkType.bdkNetwork,
        databaseConfig = sqliteConfig
      )
      .result
      .logFailure { "Error creating BDK wallet" }
  }

  /**
   * Creates unique key for identifying unique [BdkWallet] instances in the memory cache.
   */
  private fun hashKeyForWallet(walletDescriptor: WalletDescriptor): String {
    return buildString {
      append(walletDescriptor.changeDescriptor)
      append("-")
      append(walletDescriptor.receivingDescriptor.digest())
      append("-")
      append(walletDescriptor.changeDescriptor.digest())
    }
  }

  /**
   * Digests the [BitcoinDescriptor] to create a unique key for identifying
   */
  private fun BitcoinDescriptor.digest(): String = raw.encodeUtf8().sha256().sha256().utf8()
}
