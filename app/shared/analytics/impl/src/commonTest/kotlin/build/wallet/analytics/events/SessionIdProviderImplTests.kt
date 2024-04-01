package build.wallet.analytics.events

import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Duration.Companion.minutes

class SessionIdProviderImplTests : FunSpec({

  lateinit var clock: ClockFake
  lateinit var provider: SessionIdProvider

  beforeTest {
    clock = ClockFake()
    provider =
      SessionIdProviderImpl(
        clock = clock,
        uuidGenerator = UuidGeneratorFake()
      )
  }

  test("session ID updated after backgrounded for > 5 minutes") {
    val initialSessionID = provider.getSessionId()

    // Background for 6 minutes
    provider.applicationDidEnterBackground()
    clock.advanceBy(6.minutes)
    provider.applicationDidEnterForeground()

    provider.getSessionId().shouldNotBe(initialSessionID)
  }

  test("background time is not cumulative") {
    val initialSessionID = provider.getSessionId()

    // Background for 4 minutes
    provider.applicationDidEnterBackground()
    clock.advanceBy(4.minutes)
    provider.applicationDidEnterForeground()

    provider.getSessionId().shouldBe(initialSessionID)

    // Background for another 4 minutes, should not be added to last 4
    provider.applicationDidEnterBackground()
    clock.advanceBy(4.minutes)
    provider.applicationDidEnterForeground()

    provider.getSessionId().shouldBe(initialSessionID)
  }
})
