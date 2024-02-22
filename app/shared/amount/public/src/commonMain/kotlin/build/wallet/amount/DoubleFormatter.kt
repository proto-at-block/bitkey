package build.wallet.amount

interface DoubleFormatter {
  /** Formats the given double and returns the string representation.*/
  fun format(
    double: Double,
    minimumFractionDigits: Int,
    maximumFractionDigits: Int,
    isGroupingUsed: Boolean,
  ): String

  /** Parses the given string and returns the double representation if possible */
  fun parse(string: String): Double?
}
