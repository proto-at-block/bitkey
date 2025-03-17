package build.wallet.bitcoin.sync

import build.wallet.bitcoin.BitcoinNetworkType

/**
 * A data structure representing details about an Electrum server's endpoint.
 * @property url: the endpoint of our user's target Electrum server.
 */
sealed interface ElectrumServer {
  /**
   * Details about an Electrum server (host and port)
   */
  val electrumServerDetails: ElectrumServerDetails

  /**
   * Indicates a F8e-provided Electrum server.
   */
  data class F8eDefined(override val electrumServerDetails: ElectrumServerDetails) : ElectrumServer

  /**
   * Indicates a Mempool-hosted Electrum server. Its underlying value is derived from the
   * `electrumDetails` extension defined on `BitcoinNetworkType`.
   *
   * @property isAndroidEmulator used to determine local host IP
   */
  data class Mempool(
    val network: BitcoinNetworkType,
    // TODO: inject
    val isAndroidEmulator: Boolean,
  ) : ElectrumServer {
    override val electrumServerDetails: ElectrumServerDetails =
      network.mempoolElectrumServerDetails(isAndroidEmulator)
  }

  /**
   * Indicates a Blockstream-hosted Electrum server. Its underlying value is derived from the
   * `electrumDetails` extension defined on `BitcoinNetworkType`.
   *
   * @property isAndroidEmulator used to determine local host IP
   */
  data class Blockstream(
    val network: BitcoinNetworkType,
    // TODO: inject
    val isAndroidEmulator: Boolean,
  ) : ElectrumServer {
    override val electrumServerDetails: ElectrumServerDetails =
      network.blockstreamElectrumServerDetails(isAndroidEmulator)
  }

  /**
   * Indicates a user-defined Electrum server.
   */
  data class Custom(override val electrumServerDetails: ElectrumServerDetails) : ElectrumServer
}
