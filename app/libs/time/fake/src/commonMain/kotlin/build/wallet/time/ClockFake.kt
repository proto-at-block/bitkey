package build.wallet.time

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * Fake [Clock] implementation that allows to move clock forward via [advanceBy].
 */
class ClockFake(
  var now: Instant = someInstant,
) : Clock {
  override fun now(): Instant = now

  fun advanceBy(duration: Duration) {
    now += duration
  }

  fun reset() {
    now = someInstant
  }
}
