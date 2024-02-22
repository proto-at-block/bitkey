package build.wallet.analytics.events

class SessionIdProviderMock(
  private val id: String = "session-id",
) : SessionIdProvider {
  override fun getSessionId(): String = id

  override fun applicationDidEnterBackground() {}

  override fun applicationDidEnterForeground() {}
}
