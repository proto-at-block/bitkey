package build.wallet.bdk.bindings

import kotlinx.datetime.Instant

/**
 * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.28.0/bdk-ffi/src/bdk.udl#L106
 */
data class BdkBlockTime(
  val height: Long,
  val timestamp: Instant,
) {
  /**
   * Constructor for easier Swift interop.
   */
  constructor(
    height: Long,
    timestampEpochSeconds: Long,
  ) : this(
    height = height,
    timestamp = Instant.fromEpochSeconds(timestampEpochSeconds)
  )
}
