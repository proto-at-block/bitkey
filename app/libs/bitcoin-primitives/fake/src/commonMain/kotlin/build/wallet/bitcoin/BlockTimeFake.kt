package build.wallet.bitcoin

import kotlinx.datetime.Instant

val BlockTimeFake =
  BlockTime(
    height = 1L,
    timestamp = Instant.fromEpochSeconds(100)
  )
