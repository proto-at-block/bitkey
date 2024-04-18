package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter

/**
 * Delegate encoding and decoding to provided functions.
 */
internal class DelegatedColumnAdapter<T : Any>(
  private val decoder: (String) -> T,
  private val encoder: (T) -> String,
) : ColumnAdapter<T, String> {
  override fun decode(databaseValue: String) = decoder(databaseValue)

  override fun encode(value: T) = encoder(value)
}
