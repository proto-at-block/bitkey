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
      FeatureFlagValue.DoubleFlag::class ->
        database.doubleFeatureFlagQueries
          .getFlag(featureFlagId)
          .awaitAsOneOrNullResult()
          .map {
            it?.let {
              @Suppress("UNCHECKED_CAST")
              FeatureFlagValue.DoubleFlag(value = it) as T
            }
          }
      FeatureFlagValue.StringFlag::class ->
        database.stringFeatureFlagQueries
          .getFlag(featureFlagId)
          .awaitAsOneOrNullResult()
          .map {
            it?.let {
              @Suppress("UNCHECKED_CAST")
              FeatureFlagValue.StringFlag(value = it) as T
            }
          }
      else -> error("Unsupported flag type: $kClass")
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
      is FeatureFlagValue.DoubleFlag ->
        database.doubleFeatureFlagQueries
          .awaitTransaction {
            setFlag(featureFlagId, flagValue.value)
          }
      is FeatureFlagValue.StringFlag ->
        database.stringFeatureFlagQueries
          .awaitTransaction {
            setFlag(featureFlagId, flagValue.value)
          }
      else -> error("Unsupported flag type: ${flagValue::class}")
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
