package build.wallet.time

import build.wallet.catchingResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration

/**
 * Converts this string representing an instant in ISO-8601 format including date and time
 * components and the time zone offset to an [Instant] value.
 *
 * Wraps [String.toInstant] into [Result]:
 * - on success returns [Instant]
 * - on failure to parse returns [InstantParsingError]
 */
fun String.toInstantResult(): Result<Instant, InstantParsingError> {
  return catchingResult { toInstant() }
    .mapError { InstantParsingError(it) }
}

data class InstantParsingError(override val cause: Throwable) : Error()

/**
 * Returns `true` if this [Instant] is today in the current time zone.
 *
 * @param clock The [Clock] to use for the current time.
 * @param timeZoneProvider The [TimeZoneProvider] to use for the current time zone.
 */
fun Instant.isToday(
  clock: Clock,
  timeZoneProvider: TimeZoneProvider,
): Boolean {
  val now = clock.now()
  return toLocalDateTime(timeZoneProvider.current()).date ==
    now.toLocalDateTime(timeZoneProvider.current()).date
}

/**
 * Returns `true` if this [Instant] is in the current year in the current time zone.
 *
 * @param clock The [Clock] to use for the current time.
 * @param timeZoneProvider The [TimeZoneProvider] to use for the current time zone.
 */
fun Instant.isThisYear(
  clock: Clock,
  timeZoneProvider: TimeZoneProvider,
): Boolean {
  val now = clock.now()
  return toLocalDateTime(timeZoneProvider.current()).year ==
    now.toLocalDateTime(timeZoneProvider.current()).year
}

/**
 * Truncates this `Instant` to millisecond precision, removing any microseconds or nanoseconds.
 *
 * If the `Instant` already has millisecond precision, it remains unchanged.
 * Any extra precision (like microseconds or nanoseconds) is discarded, rounding down.
 *
 * Example:
 * ```
 * val instant = "2023-01-10T08:22:25.736822Z".toInstant()
 * val truncated = instant.truncateToMilliseconds()
 * // output: 2023-01-10T08:22:25.736Z
 * ```
 */
fun Instant.truncateToMilliseconds(): Instant =
  Instant.fromEpochMilliseconds(this.toEpochMilliseconds())

/**
 * Truncates this [Instant] to a multiple of the [duration].
 *
 * This is useful when aligning or grouping time ranges or
 * a series of Instants on a consistently timed interval.
 *
 * ```
 * val now = Instant.parse("2025-02-15T12:46:44Z")
 *
 * assertEquals("2025-02-15T12:40:00Z", now.truncateTo(10.minutes).toString())
 * assertEquals("2025-02-15T12:00:00Z", now.truncateTo(1.hours).toString())
 * assertEquals("2025-02-15T00:00:00Z", now.truncateTo(1.days).toString())
 * ```
 */
fun Instant.truncateTo(duration: Duration): Instant {
  val intervalSeconds = duration.inWholeSeconds
  return Instant.fromEpochSeconds(
    (epochSeconds / intervalSeconds) * intervalSeconds
  )
}
