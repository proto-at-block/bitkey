package build.wallet.time

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.offsetAt
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.DurationUnit.SECONDS
import kotlin.time.toDuration

interface TimeZoneFormatter {
  fun timeZoneShortName(
    timeZone: TimeZone,
    localeIdentifier: String,
  ): String
}

/**
 * Returns a string with timezone identifier and hours offset from UTC, for example "PDT (UTC -8)".
 */
fun TimeZoneFormatter.timeZoneShortNameWithHoursOffset(
  timeZone: TimeZone,
  clock: Clock,
  localeIdentifier: String,
): String {
  val timeZoneShortName = timeZoneShortName(timeZone, localeIdentifier)
  val hoursFromUTC = timeZone.hoursFromUtc(clock)
  return "$timeZoneShortName (UTC $hoursFromUTC)"
}

fun TimeZone.hoursFromUtc(clock: Clock): Int = offsetAt(clock.now()).duration().inWholeHours.toInt()

fun TimeZone.timeFromUtcInHms(clock: Clock): String {
  val offsetInSeconds = offsetAt(clock.now()).duration().inWholeSeconds
  val isNegative = offsetInSeconds < 0
  val totalSeconds = abs(offsetInSeconds)
  val hours = (totalSeconds / 3600).toInt()
  val minutes = ((totalSeconds - hours * 3600) / 60).toInt()
  val seconds = (totalSeconds - (hours * 3600) - (minutes * 60)).toInt()

  return buildString {
    if (isNegative) {
      append("-")
    } else {
      append("+")
    }
    if (hours < 10) append("0")
    append(hours)
    append(":")
    if (minutes < 10) append("0")
    append(minutes)
    append(":")
    if (seconds < 10) append("0")
    append(seconds)
  }
}

private fun UtcOffset.duration(): Duration = totalSeconds.toDuration(SECONDS)
