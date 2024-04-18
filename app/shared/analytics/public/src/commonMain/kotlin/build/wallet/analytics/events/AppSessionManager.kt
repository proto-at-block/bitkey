package build.wallet.analytics.events

import kotlinx.coroutines.flow.StateFlow

/**
 * Provides the current session ID for the app.
 *
 * Session IDs are generated at app launch and refreshed if the app is backgrounded for
 * more than 5 minutes.
 */
interface AppSessionManager {
  /** Flow with the latest [AppSessionState] */
  val appSessionState: StateFlow<AppSessionState>

  /** Returns the current session ID for the app */
  fun getSessionId(): String

  /** Should be called by platform-specific code when the app enters the background */
  fun appDidEnterBackground()

  /** Should be called by platform-specific code when the app enters the foreground */
  fun appDidEnterForeground()

  /** Returns true if the app is currently foregrounded */
  fun isAppForegrounded(): Boolean
}

enum class AppSessionState {
  /**
   * The app is currently in the background, meaning it is not visible to the customer.
   */
  BACKGROUND,

  /**
   * The app is currently in the foreground, meaning it is visible to the customer.
   */
  FOREGROUND,
}
