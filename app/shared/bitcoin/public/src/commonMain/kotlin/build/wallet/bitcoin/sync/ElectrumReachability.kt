package build.wallet.bitcoin.sync

import build.wallet.bdk.bindings.BdkError
import build.wallet.bitcoin.BitcoinNetworkType
import com.github.michaelbull.result.Result

/**
 * Helper class for determining whether or not a specific Electrum server endpoint is reachable.
 */
interface ElectrumReachability {
  /**
   * Indicates whether or not the app was able to reach an Electrum server.
   * @param electrumServer: target server we would like to test connectivity against.
   * @param network: the user's intended bitcoin network.
   */
  suspend fun reachable(
    electrumServer: ElectrumServer,
    network: BitcoinNetworkType,
  ): Result<Unit, ElectrumReachabilityError>

  /**
   * Indicates an error during the process of testing if we can reach an Electrum server.
   */
  sealed class ElectrumReachabilityError : Error() {
    /** The Electrum server was unreachable or unresponsive over the network. */
    data class Unreachable(val bdkError: BdkError) : ElectrumReachabilityError() {
      override val cause: Throwable? = bdkError.cause
    }

    /** The Electrum server the user is trying to reach is incompatible with their active keyset. */
    data object IncompatibleNetwork : ElectrumReachabilityError()
  }
}
