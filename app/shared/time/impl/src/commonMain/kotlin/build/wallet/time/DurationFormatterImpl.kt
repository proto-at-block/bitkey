package build.wallet.time

import kotlin.time.Duration

class DurationFormatterImpl : DurationFormatter {
  override fun formatWithWords(duration: Duration): String {
    return duration.toComponents { days, hours, minutes, _, _ ->
      when {
        days > 0 -> {
          when {
            hours > 0 -> "$days days, $hours hours"
            else -> "$days days"
          }
        }
        hours > 0 -> {
          when {
            minutes > 0 -> "$hours hours, $minutes minutes"
            else -> "$hours hours"
          }
        }
        minutes > 0 -> "$minutes minutes"
        else -> "Less than 1 minute"
      }
    }
  }

  override fun formatWithAlphabet(duration: Duration): String {
    return duration.toComponents { days, hours, minutes, _, _ ->
      when {
        days > 0 -> {
          when {
            hours > 0 -> "${days}d ${hours}h"
            else -> "${days}d"
          }
        }
        hours > 0 -> {
          when {
            minutes > 0 -> "${hours}h ${minutes}m"
            else -> "${hours}h"
          }
        }
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
      }
    }
  }

  override fun formatWithMMSS(duration: Duration): String {
    val minutes = duration.inWholeMinutes.padStartTo2()
    val seconds = (duration.inWholeSeconds % 60).padStartTo2()
    return "$minutes:$seconds"
  }
}

private fun Long.padStartTo2(): String {
  return if (toString().length < 2) {
    "0$this"
  } else {
    this.toString()
  }
}
