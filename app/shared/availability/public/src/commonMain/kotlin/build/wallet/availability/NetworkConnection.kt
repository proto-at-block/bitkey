package build.wallet.availability

import build.wallet.f8e.F8eEnvironment
import kotlinx.serialization.Serializable

/**
 * Represents an object that forms a network connection in the app.
 */
@Serializable
sealed interface NetworkConnection {
  /**
   * Connections that use HttpClient (ktor)
   */
  @Serializable
  sealed interface HttpClientNetworkConnection : NetworkConnection {
    @Serializable
    data object Bitstamp : HttpClientNetworkConnection

    @Serializable
    data class F8e(val environment: F8eEnvironment) : HttpClientNetworkConnection

    @Serializable
    data object Memfault : HttpClientNetworkConnection

    @Serializable
    data object Mempool : HttpClientNetworkConnection
  }

  /**
   * Connection when trying to sync the electrum server
   */
  @Serializable
  data object ElectrumSyncerNetworkConnection : NetworkConnection

  /**
   * Connection returned from DB in [NetworkConnecitonColumnAdapter] when the stored
   * string DB value cannot be decoded.
   */
  @Serializable
  data object UnknownNetworkConnection : NetworkConnection
}
