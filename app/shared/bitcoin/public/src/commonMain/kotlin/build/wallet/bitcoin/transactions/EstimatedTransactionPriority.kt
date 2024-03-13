package build.wallet.bitcoin.transactions

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

/*
 * Provides a [Duration] based on the variant of [EstimatedTransactionPriority]
 */
fun EstimatedTransactionPriority.toDuration(): Duration {
  return when (this) {
    EstimatedTransactionPriority.FASTEST -> 10.toDuration(MINUTES)
    EstimatedTransactionPriority.THIRTY_MINUTES -> 30.toDuration(MINUTES)
    EstimatedTransactionPriority.SIXTY_MINUTES -> 60.toDuration(MINUTES)
  }
}

/*
 * Provides a [ULong] based on how many target blocks
 */
fun EstimatedTransactionPriority.targetBlocks(): ULong {
  return when (this) {
    EstimatedTransactionPriority.FASTEST -> 1UL
    EstimatedTransactionPriority.THIRTY_MINUTES -> 3UL
    EstimatedTransactionPriority.SIXTY_MINUTES -> 6UL
  }
}
