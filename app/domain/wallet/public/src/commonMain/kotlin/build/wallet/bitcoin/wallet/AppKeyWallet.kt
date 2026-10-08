package build.wallet.bitcoin.wallet

import com.github.michaelbull.result.Result

/**
 * A native-SegWit wallet controlled only by an app spending key.
 *
 * A full scan is explicit because ordinary incremental synchronization only checks previously
 * revealed scripts and can miss externally derived addresses.
 */
interface AppKeyWallet : SpendingWallet {
  suspend fun fullScan(): Result<Unit, Error>
}
