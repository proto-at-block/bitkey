package build.wallet.time

import build.wallet.Progress
import build.wallet.asProgress
import build.wallet.catchingResult
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
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
): Result<Progress, Throwable> =
  binding {
    val progressValue: Float = when {
      now >= endTime -> 1.0f
      now <= startTime -> 0.0f
      else -> {
        val startTimeNormalized = maxOf(now - startTime, Duration.ZERO)
        val endTimeNormalized = endTime - startTime
        // run catching to handle division by zero
        catchingResult { startTimeNormalized.inWholeMilliseconds / endTimeNormalized.inWholeMilliseconds.toFloat() }
          .bind()
      }
    }

    progressValue.asProgress().bind()
  }.logFailure {
    "Error calculating duration progress: now==$now, startTime=$startTime, endTime=$endTime"
  }
