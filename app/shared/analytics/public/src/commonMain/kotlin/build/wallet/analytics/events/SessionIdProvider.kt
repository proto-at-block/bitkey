package build.wallet.analytics.events

/**
 * Provides the current session ID for the device.
 *
 * Session IDs are generated at app launch and refreshed if the app is backgrounded for
 * more than 5 minutes.
 */
interface SessionIdProvider {
  /** Returns the current session ID for the device */
  fun getSessionId(): String

  /** Should be called by platform-specific code when the app enters the background */
  fun applicationDidEnterBackground()

  /** Should be called by platform-specific code when the app enters the foreground */
  fun applicationDidEnterForeground()
}
