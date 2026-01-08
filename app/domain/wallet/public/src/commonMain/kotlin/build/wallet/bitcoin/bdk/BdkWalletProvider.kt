package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkWallet
import build.wallet.bitcoin.wallet.WalletDescriptor
import com.github.michaelbull.result.Result
import uniffi.bdk.Persister
import uniffi.bdk.Wallet as BdkV2Wallet

interface BdkWalletProvider {
  /**
   * Creates and caches instances of a [BdkWallet] for given [walletIdentifier] and descriptors.
   */
  suspend fun getBdkWallet(walletDescriptor: WalletDescriptor): Result<BdkWallet, BdkError>

  /**
   * Creates and caches instances of a BDK v2 [BdkV2Wallet] for given descriptors
   */
  fun getBdkWalletV2(walletDescriptor: WalletDescriptor): Result<BdkV2Wallet, Throwable>

  /**
   * Gets or creates a [Persister] for the given wallet identifier (BdkV2 only)
   */
  fun getPersister(identifier: String): Persister
}
