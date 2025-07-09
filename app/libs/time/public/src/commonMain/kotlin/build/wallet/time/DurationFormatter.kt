package build.wallet.time

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface DurationFormatter {
  /**
   * Formats with words, i.e.
   * "15 days" or "2 days, 3 hours" or "2 hours, 5 minutes" or "Less than 1 minute"
   */
  fun formatWithWords(duration: Duration): String

  /**
   * Formats with mm:ss timestamp, i.e.
   * 00:25 for 25 seconds
   */
  fun formatWithMMSS(duration: Duration): String

  /**
   * Formats with alphabets i.e.
   * "15d" or "2d 3h" or "2h 5m" or "<1m"
   */
  fun formatWithAlphabet(duration: Duration): String

  /**
   * [Duration] is an inline class which is not supported in all KMP targets.
   *
   * Compatibility API for iOS to be used with alias
   * [TimeInterval](https://developer.apple.com/documentation/foundation/timeinterval) that
   * represents seconds.
   */
  fun formatWithWords(seconds: Double): String = formatWithWords(seconds.seconds)
}
