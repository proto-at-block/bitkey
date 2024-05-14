package build.wallet

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlin.jvm.JvmInline

/**
 * Represents a progress value between 0.0f and 1.0f, where 0.0f is 0% and 1.0f is 100%.
 * So 0.5f would be 50%, 0.75f would be 75%, etc.
 *
 * Note that the main purpose of this type is provide a stronger type safety around progress values,
 * and to improve readability in the codebase. This type is not meant to be used for complex and
 * precise calculations.
 *
 * This type is intentionally named `Progress` and not `Percentage` since this value is meant
 * to specifically represent a bounded progress value between 0.0f and 1.0f as a percentage.
 *
 * [Float.asProgress] is the main way to create a [Progress] instance, for example:
 * ```kotlin
 * val progress = 0.5f.asProgress().bind() // 50%
 * ```
 */
@JvmInline
value class Progress internal constructor(
  val value: Float,
) {
  companion object {
    val Zero = Progress(0f)
    val Half = Progress(0.5f)
    val Full = Progress(1f)
  }

  /**
   * Returns a [String] representation of the progress value as a percentage.
   *
   * Rounds to two decimal places for simplicity.
   */
  override fun toString(): String {
    val percentageValue = value
      .toBigDecimal()
      .multiply(100.toBigDecimal())
      .toPlainString()
    return "$percentageValue%"
  }
}

/**
 * Main way to create a [Progress] instance, where [Double] value must be between 0.0 and 1.0.
 *
 * If the value is out of bounds, an [Error] result is returned.
 */
fun Float.asProgress(): Result<Progress, Error> {
  return when {
    this < 0.0 -> Err(Error("Progress cannot be negative."))
    this > 1.0 -> Err(Error("Progress cannot be greater than 1."))
    else -> Ok(Progress(this))
  }
}
