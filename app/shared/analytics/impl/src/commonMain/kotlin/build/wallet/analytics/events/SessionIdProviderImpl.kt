package build.wallet.analytics.events

import build.wallet.logging.log
import build.wallet.platform.random.Uuid
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

class SessionIdProviderImpl(
  private val clock: Clock,
  private val uuid: Uuid,
) : SessionIdProvider {
  // Keep track of the time the application entered the background
  // in order to calculate how long it was backgrounded
  private var applicationDidEnterBackgroundTime: Instant? = null

  // Generate the initial session ID at initialization of this class.
  // It should be unique for each app launch and only refreshed if backgrounded
  // for more than 5 minutes.
  private var sessionId = uuid.random()

  override fun getSessionId(): String = sessionId

  override fun applicationDidEnterBackground() {
    applicationDidEnterBackgroundTime = clock.now()
  }

  override fun applicationDidEnterForeground() {
    if (applicationDidEnterBackgroundTime == null) {
      return
    }

    // Update the session ID if the app spent > 5 minutes backgrounded
    val timeInBackground = clock.now() - applicationDidEnterBackgroundTime!!
    if (timeInBackground > 5.minutes) {
      sessionId = uuid.random()
      log { "Refreshing session ID" }
    }
    applicationDidEnterBackgroundTime = null
  }
}
