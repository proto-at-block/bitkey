package build.wallet.bitcoin.lightning

import build.wallet.ldk.bindings.PublicKey
import build.wallet.ldk.bindings.SocketAddr

/**
 * Represents connection information about a peer on the Lightning Network
 */
data class Peer(
  /**
   * Our peer's public key, which uniquely identifies them on the network.
   */
  val nodeId: PublicKey,
  /**
   * String representation of our peer's IP address, with port number.
   *
   * This is currently typealias-ed to `String`, and expects ipv4/ipv6 addresses and port delimited
   * by `:`. Here are examples of strings that would work:
   *
   * - 3.209.126.167:39735
   * - Light-Light-ZN5BZS6939KD-bb0d88632b07dc9b.elb.us-east-1.amazonaws.com:39735
   * - 2001:0db8:85a3:0000:0000:8a2e:0370:7334:39735
   *
   * In future, this will likely change to an API with more type assurances.
   */
  val address: SocketAddr,
)
