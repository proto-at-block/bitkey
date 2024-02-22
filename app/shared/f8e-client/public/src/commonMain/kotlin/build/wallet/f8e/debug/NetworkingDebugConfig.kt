package build.wallet.f8e.debug

data class NetworkingDebugConfig(
  /**
   * If `true`, all f8e http requests will fail. This option is helpful for testing app behavior
   * when Block is down.
   */
  val failF8eRequests: Boolean,
) {
  companion object
}
