package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkError
import build.wallet.bitcoin.BitcoinNetworkType
import com.github.michaelbull.result.Result
import uniffi.bdk.Persister
import uniffi.bdk.Wallet

/**
 * Syncs a BDK v2 wallet against the blockchain.
 */
interface BdkWalletSyncerV2 {
  suspend fun sync(
    bdkWallet: Wallet,
    persister: Persister,
    networkType: BitcoinNetworkType,
  ): Result<Unit, BdkError>
}
