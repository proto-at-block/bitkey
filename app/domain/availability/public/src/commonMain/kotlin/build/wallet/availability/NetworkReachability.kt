package build.wallet.availability

/**
 * Reachability of a network connection in the app.
 */
enum class NetworkReachability {
  /** Indicates that a connection was able to be established */
  REACHABLE,

  /**
   * Indicates that the connection could not be reached, either because a connection could not
   * be formed at all (i.e. timeout, no internet), or there is an issue with the connection,
   * and it is returning errors.
   */
  UNREACHABLE,
}
