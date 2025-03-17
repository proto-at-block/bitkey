package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter

/**
 * Adapts a set to a comma separated string.
 */
internal class StringSetAdapter<T : Any>(
  private val decoder: (String) -> T,
  private val encoder: (T) -> String,
) : ColumnAdapter<Set<T>, String> {
  constructor(
    typeAdapter: ColumnAdapter<T, String>,
  ) : this(
    decoder = typeAdapter::decode,
    encoder = typeAdapter::encode
  )

  override fun decode(databaseValue: String): Set<T> {
    return databaseValue.split(",").map { decoder(it) }.toSet()
  }

  override fun encode(value: Set<T>): String {
    return value.joinToString(",") { encoder(it) }
  }

  companion object {
    val PlainStringSet = StringSetAdapter(
      decoder = { it },
      encoder = { it }
    )
  }
}
