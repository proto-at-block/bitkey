package build.wallet.platform.connectivity

/**
 * Checks if the device has a working internet connection.
 *
 * Platform behavior:
 * - **Android**: Checks `NET_CAPABILITY_VALIDATED` and `NET_CAPABILITY_INTERNET`
 *   on the active network.
 * - **iOS**: Checks NWPathMonitor path status.
 * - **JVM**: Returns true.
 */
interface InternetConnectionChecker {
  /**
   * Returns true if the device has a working internet connection.
   */
  fun isConnected(): Boolean
}
