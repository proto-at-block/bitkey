package build.wallet.testing

import eu.rekawek.toxiproxy.Proxy
import eu.rekawek.toxiproxy.ToxiproxyClient

/**
 * Type-safe wrapper class for configuring ToxiProxy.
 */
class ToxiProxyService(
  val host: String = "localhost",
  val port: Int = 8474,
) {
  private val client by lazy { ToxiproxyClient(host, port) }

  val fromagerie: Proxy
    get() = client.getProxy(Instance.FROMAGERIE.proxyName)

  val electrumRpc: Proxy
    get() = client.getProxy(Instance.ELECTRUM_RPC.proxyName)

  fun reset() {
    client.reset()
  }

  /**
   * Configured proxy instances. These must match values in
   * https://github.com/squareup/wallet/tree/main/server/toxiproxy.json
   */
  enum class Instance(val proxyName: String) {
    FROMAGERIE("fromagerie"),
    ELECTRUM_RPC("app_electrum_rpc"),
  }
}
