package build.wallet.bitcoin.transactions

import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.*
import kotlin.time.Duration
import kotlin.time.DurationUnit.MINUTES
import kotlin.time.toDuration

/**
 * Used to determine the fee necessary for a given priority
 */
enum class EstimatedTransactionPriority : Comparable<EstimatedTransactionPriority> {
  /**
   * The fastest priority for completion, estimated to be ~10 mins
   */
  FASTEST,

  /**
   * Priority estimated to complete in ~30 mins
   */
  THIRTY_MINUTES,

  /**
   * Priority estimated to complete in ~60 mins
   */
  SIXTY_MINUTES,

  ;

  companion object {
    fun sweepPriority(): EstimatedTransactionPriority = THIRTY_MINUTES
  }
}

/**
 * Provides a [Duration] based on the variant of [EstimatedTransactionPriority]
 */
fun EstimatedTransactionPriority.toDuration(): Duration {
  return when (this) {
    FASTEST -> 10.toDuration(MINUTES)
    THIRTY_MINUTES -> 30.toDuration(MINUTES)
    SIXTY_MINUTES -> 60.toDuration(MINUTES)
  }
}

/**
 * Provides a [ULong] based on how many target blocks
 */
fun EstimatedTransactionPriority.targetBlocks(): ULong {
  return when (this) {
    FASTEST -> 1UL
    THIRTY_MINUTES -> 3UL
    SIXTY_MINUTES -> 6UL
  }
}

/**
 * Formats this [EstimatedTransactionPriority] to a human-readable time estimation string.
 */
fun EstimatedTransactionPriority.toFormattedString(): String {
  return when (this) {
    FASTEST -> "~10 minutes"
    THIRTY_MINUTES -> "~30 minutes"
    SIXTY_MINUTES -> "~60 minutes"
  }
}
