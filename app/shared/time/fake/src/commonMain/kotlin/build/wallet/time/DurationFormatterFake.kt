package build.wallet.time

import kotlin.time.Duration

/**
 * Fake [DurationFormatter] implementation that formats durations using
 * [Duration.toString].
 */
class DurationFormatterFake : DurationFormatter {
  override fun formatWithWords(duration: Duration) = duration.toString()

  override fun formatWithMMSS(duration: Duration) = duration.toString()

  override fun formatWithAlphabet(duration: Duration) = duration.toString()
}
