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
import build.wallet.platform.data.File.join
import build.wallet.platform.data.FileDirectoryProvider
import build.wallet.platform.data.databasesDir
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import uniffi.bdk.Descriptor
import uniffi.bdk.Persister
import uniffi.bdk.Wallet as BdkV2Wallet

/**
 * Creates and caches [BdkWallet] instances. This class should be initialized
 * as a singleton in the DI graph.
 */

@BitkeyInject(AppScope::class)
class BdkWalletProviderImpl(
  private val bdkWalletFactory: BdkWalletFactory,
  private val bdkDatabaseConfigProvider: BdkDatabaseConfigProvider,
  private val fileDirectoryProvider: FileDirectoryProvider,
) : BdkWalletProvider {
  /**
   * Using suspending lock to ensure that we maintain a single [BdkWallet] instance
   * per descriptor set. This is to avoid BDK race conditions: https://github.com/bitcoindevkit/bdk/issues/915.
   */
  private val bdkLock = Mutex(locked = false)
  private val wallets = mutableMapOf<String, BdkWallet>()
  private val walletsV2 = mutableMapOf<String, BdkV2Wallet>()
  private val persisters = mutableMapOf<String, Persister>()

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

  override fun getBdkWalletV2(walletDescriptor: WalletDescriptor): Result<BdkV2Wallet, Throwable> {
    val key = walletDescriptor.identifier
    walletsV2[key]?.let { return Ok(it) }

    val persister = getPersister(walletDescriptor.identifier)
    val network = walletDescriptor.networkType.bdkNetworkV2
    val externalDescriptor = Descriptor(walletDescriptor.receivingDescriptor.raw, network)
    val internalDescriptor = Descriptor(walletDescriptor.changeDescriptor.raw, network)

    return runCatching {
      runCatching {
        BdkV2Wallet.load(externalDescriptor, internalDescriptor, persister)
      }.recoverCatching {
        BdkV2Wallet(externalDescriptor, internalDescriptor, network, persister)
      }.getOrThrow()
    }.fold(
      onSuccess = { wallet ->
        walletsV2[key] = wallet
        Ok(wallet)
      },
      onFailure = { Err(it) }
    )
  }

  override fun getPersister(identifier: String): Persister {
    return persisters.getOrPut(identifier) {
      val databaseFileName = "$identifier-bdk.db"
      val databaseDir = fileDirectoryProvider.databasesDir()
      FileSystem.SYSTEM.createDirectories(databaseDir.toPath())
      val databasePath = databaseDir.join(databaseFileName)
      Persister.newSqlite(databasePath)
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
