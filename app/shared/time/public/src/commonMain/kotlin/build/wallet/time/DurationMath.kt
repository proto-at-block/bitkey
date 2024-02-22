package build.wallet.time

import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * Calculates duration between [startTime] and [endTime]
 *
 * If [startTime] is over [endTime] returns [Duration.ZERO], otherwise returns duration.
 */
fun nonNegativeDurationBetween(
  startTime: Instant,
  endTime: Instant,
): Duration = maxOf(endTime - startTime, Duration.ZERO)

/**
 * Given some start and end time, calculate duration progress in relation to current clock time.
 *
 * For example, if current clock is at epoch 100 seconds, start time is at epoch 50
 * seconds, end time is at epoch 150 seconds, then the progress is calculated at
 * 0.5f (50%).
 */
fun durationProgress(
  now: Instant,
  startTime: Instant,
  endTime: Instant,
): Float =
  when {
    now >= endTime -> 1f
    now <= startTime -> 0f
    else -> {
      val startTimeNormalized = maxOf(now - startTime, Duration.ZERO)
      val endTimeNormalized = endTime - startTime
      startTimeNormalized.inWholeMilliseconds / endTimeNormalized.inWholeMilliseconds.toFloat()
    }
  }
