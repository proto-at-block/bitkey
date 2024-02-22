package build.wallet.bitcoin

import kotlinx.datetime.Instant

data class BlockTime(
  val height: Long,
  val timestamp: Instant,
)
