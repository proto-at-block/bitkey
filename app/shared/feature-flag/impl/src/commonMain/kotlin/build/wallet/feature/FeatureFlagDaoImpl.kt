package build.wallet.feature

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlin.reflect.KClass

class FeatureFlagDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : FeatureFlagDao {
  private val database by lazy {
    databaseProvider.database()
  }

  override suspend fun <T : FeatureFlagValue> getFlag(
    featureFlagId: String,
    kClass: KClass<T>,
  ): Result<T?, DbError> {
    return when (kClass) {
      BooleanFlag::class ->
        database.booleanFeatureFlagQueries
          .getFlag(featureFlagId)
          .awaitAsOneOrNullResult()
          .map { getFlagValue ->
            getFlagValue?.let {
              @Suppress("UNCHECKED_CAST")
              BooleanFlag(value = it) as T
            }
          }
      else -> TODO("Not yet implemented")
    }
  }

  override suspend fun <T : FeatureFlagValue> setFlag(
    flagValue: T,
    featureFlagId: String,
  ): Result<Unit, DbError> {
    return when (flagValue) {
      is BooleanFlag ->
        database.booleanFeatureFlagQueries
          .awaitTransaction {
            setFlag(featureFlagId, flagValue.value)
          }

      else -> TODO("Not yet implemented")
    }
  }

  override suspend fun getFlagOverridden(featureFlagId: String): Result<Boolean, DbError> =
    database
      .featureFlagOverrideQueries
      .getFlagOverridden(featureFlagId)
      .awaitAsOneOrNullResult()
      .map { it ?: false }

  override suspend fun setFlagOverridden(
    featureFlagId: String,
    overridden: Boolean,
  ): Result<Unit, DbError> =
    database
      .featureFlagOverrideQueries
      .awaitTransaction {
        setFlagOverridden(featureFlagId, overridden)
      }
}
