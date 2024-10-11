package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter

/**
 * Delegate encoding and decoding to provided functions.
 */
internal class DelegatedColumnAdapter<A : Any, B : Any>(
  private val decoder: (B) -> A,
  private val encoder: (A) -> B,
) : ColumnAdapter<A, B> {
  override fun decode(databaseValue: B) = decoder(databaseValue)

  override fun encode(value: A) = encoder(value)
}

/**
 * Chains two adapters together.
 */
internal fun <A : Any, B : Any, C : Any> ColumnAdapter<A, B>.then(
  second: ColumnAdapter<B, C>,
): ColumnAdapter<A, C> {
  val first = this
  return DelegatedColumnAdapter(
    decoder = { first.decode(second.decode(it)) },
    encoder = { second.encode(first.encode(it)) }
  )
}
