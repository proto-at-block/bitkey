package build.wallet.analytics.events

import build.wallet.logging.log
import build.wallet.platform.random.UuidGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

class AppSessionManagerImpl(
  private val clock: Clock,
  private val uuidGenerator: UuidGenerator,
) : AppSessionManager {
  // Keep track of the time the application entered the background
  // in order to calculate how long it was backgrounded
  private var applicationDidEnterBackgroundTime: Instant? = null

  // Generate the initial session ID at initialization of this class.
  // It should be unique for each app launch and only refreshed if backgrounded
  // for more than 5 minutes.
  private var sessionId = uuidGenerator.random()

  private val appSessionStateFlow = MutableStateFlow(AppSessionState.FOREGROUND)

  override val appSessionState: StateFlow<AppSessionState> = appSessionStateFlow

  override fun getSessionId(): String = sessionId

  override fun appDidEnterBackground() {
    appSessionStateFlow.value = AppSessionState.BACKGROUND
    applicationDidEnterBackgroundTime = clock.now()
  }

  override fun appDidEnterForeground() {
    appSessionStateFlow.value = AppSessionState.FOREGROUND
    if (applicationDidEnterBackgroundTime == null) {
      return
    }

    // Update the session ID if the app spent > 5 minutes backgrounded
    val timeInBackground = clock.now() - applicationDidEnterBackgroundTime!!
    if (timeInBackground > 5.minutes) {
      sessionId = uuidGenerator.random()
      log { "Refreshing session ID" }
    }
    applicationDidEnterBackgroundTime = null
  }

  override fun isAppForegrounded(): Boolean {
    return appSessionStateFlow.value == AppSessionState.FOREGROUND
  }
}
