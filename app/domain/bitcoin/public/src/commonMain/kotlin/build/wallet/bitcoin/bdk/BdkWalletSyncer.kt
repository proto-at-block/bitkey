package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkWallet
import build.wallet.bitcoin.BitcoinNetworkType
import com.github.michaelbull.result.Result

interface BdkWalletSyncer {
  suspend fun sync(
    bdkWallet: BdkWallet,
    networkType: BitcoinNetworkType,
  ): Result<Unit, BdkError>
}
