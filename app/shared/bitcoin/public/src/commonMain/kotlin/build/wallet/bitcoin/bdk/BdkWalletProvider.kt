package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkWallet
import build.wallet.bitcoin.wallet.WalletDescriptor
import com.github.michaelbull.result.Result

interface BdkWalletProvider {
  /**
   * Creates and caches instances of a [BdkWallet] for given [walletIdentifier] and descriptors.
   */
  suspend fun getBdkWallet(walletDescriptor: WalletDescriptor): Result<BdkWallet, BdkError>
}
