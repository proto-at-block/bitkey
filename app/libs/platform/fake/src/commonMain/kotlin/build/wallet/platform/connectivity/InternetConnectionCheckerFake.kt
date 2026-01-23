package build.wallet.platform.connectivity

class InternetConnectionCheckerFake : InternetConnectionChecker {
  var connected: Boolean = true

  override fun isConnected(): Boolean {
    return connected
  }

  fun reset() {
    connected = true
  }
}
