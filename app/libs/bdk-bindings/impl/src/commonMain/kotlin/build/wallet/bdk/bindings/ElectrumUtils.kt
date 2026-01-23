package build.wallet.bdk.bindings

import build.wallet.catchingResult
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.mapError
import uniffi.bdk.ElectrumException

/**
 * Shared helpers for translating Electrum exceptions into [BdkError] and wrapping results.
 */
internal inline fun <V> runCatchingElectrum(block: () -> V): BdkResult<V> =
  catchingResult { block() }
    .mapError(Throwable::asBdkError)
    .mapBoth(
      success = { value -> BdkResult.Ok(value) },
      failure = { error -> BdkResult.Err(error) }
    )

internal fun Throwable.asBdkError(): BdkError =
  when (this) {
    is ElectrumException -> BdkError.Electrum(cause = this, message = message ?: "Electrum error")
    is InvalidFeeRateException -> BdkError.FeeRateUnavailable(cause = this, message = message)
    else -> BdkError.Generic(cause = this, message = message ?: "Unknown error")
  }

/**
 * Conversion helper from BTC/kB to sats/vB.
 */
internal fun btcPerKbToSatsPerVb(btcPerKb: Double): Float = (btcPerKb * 100_000.0).toFloat()

/**
 * Thrown when Electrum returns an invalid fee rate (e.g., NaN, negative, or zero).
 * Mapped to [BdkError.FeeRateUnavailable].
 */
internal class InvalidFeeRateException(message: String) : IllegalArgumentException(message)
