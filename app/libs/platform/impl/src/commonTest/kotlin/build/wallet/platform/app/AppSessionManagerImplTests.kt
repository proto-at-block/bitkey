package build.wallet.platform.app

import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes

class AppSessionManagerImplTests : FunSpec({

  val clock = ClockFake()
  val uuidGenerator = UuidGeneratorFake()
  lateinit var appSessionManager: AppSessionManagerImpl

  beforeTest {
    clock.reset()
    uuidGenerator.reset()

    // Creating new instance for each test because the session ID is generated at initialization
    appSessionManager = AppSessionManagerImpl(clock, uuidGenerator)
  }

  test("initial session ID is generated at initialization and is not changed") {
    val sessionId = "uuid-0"
    appSessionManager.getSessionId().shouldBe(sessionId)
    appSessionManager.getSessionId().shouldBe(sessionId)
  }

  test("session ID updated after backgrounded for > 5 minutes") {
    // Background for 6 minutes
    appSessionManager.appDidEnterBackground()
    clock.advanceBy(6.minutes)
    appSessionManager.appDidEnterForeground()

    appSessionManager.getSessionId().shouldBe("uuid-1")
  }

  test("background time is not cumulative") {
    // Background for 4 minutes
    appSessionManager.appDidEnterBackground()
    clock.advanceBy(4.minutes)
    appSessionManager.appDidEnterForeground()

    appSessionManager.getSessionId().shouldBe("uuid-0")

    // Background for another 4 minutes, should not be added to last 4
    appSessionManager.appDidEnterBackground()
    clock.advanceBy(4.minutes)
    appSessionManager.appDidEnterForeground()

    appSessionManager.getSessionId().shouldBe("uuid-0")
  }

  test("session state flow updates correctly when app enters background and foreground") {
    val sessionState = appSessionManager.appSessionState
    sessionState.value shouldBe AppSessionState.FOREGROUND

    appSessionManager.appDidEnterBackground()
    sessionState.value shouldBe AppSessionState.BACKGROUND

    appSessionManager.appDidEnterForeground()
    sessionState.value shouldBe AppSessionState.FOREGROUND
  }
})
