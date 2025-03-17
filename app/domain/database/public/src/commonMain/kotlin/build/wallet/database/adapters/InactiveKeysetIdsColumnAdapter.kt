package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter

/**
 * SqlDelight column adapter for set of inactive keyset IDs.
 *
 * Encodes as a string of IDs separated by comma.
 */
internal object InactiveKeysetIdsColumnAdapter : ColumnAdapter<Set<String>, String> {
  override fun decode(databaseValue: String) =
    if (databaseValue.isEmpty()) {
      emptySet()
    } else {
      databaseValue.split(",").map { it }.toSet()
    }

  override fun encode(value: Set<String>) = value.joinToString(separator = ",")
}
