package build.wallet.bitcoin.export

import com.github.michaelbull.result.Result

/*
 * A service for exporting watching (aka public) descriptors for a wallet.
 */
interface ExportWatchingDescriptorService {
  /*
   * Exports both external and change descriptors as a human-readable string.
   */
  suspend fun formattedActiveWalletDescriptorString(): Result<String, Throwable>
}
