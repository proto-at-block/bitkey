package build.wallet.db

/**
 * Describes a database error
 */
sealed class DbError : Error()

/**
 * A failure which can be resulted as part of executing database queries.
 */
data class DbQueryError(
  override val cause: Throwable?,
  override val message: String? = cause?.message,
) : DbError()

/**
 * A failure which can be resulted as part of executing database transactions.
 */
data class DbTransactionError(
  override val cause: Throwable,
  override val message: String? = cause.message,
) : DbError()
