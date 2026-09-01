package build.wallet.feature

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.sqldelight.awaitAsListResult
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlin.reflect.KClass

@BitkeyInject(AppScope::class)
class FeatureFlagDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : FeatureFlagDao {
  override suspend fun getFlags(): Result<Map<String, FeatureFlagValue>, DbError> =
    databaseProvider.database().featureFlagsQueries
      .getAllFlags { featureFlagId, booleanValue, doubleValue, stringValue ->
        featureFlagId to when {
          booleanValue != null -> BooleanFlag(booleanValue)
          doubleValue != null -> FeatureFlagValue.DoubleFlag(doubleValue)
          stringValue != null -> FeatureFlagValue.StringFlag(stringValue)
          else -> error("Persisted feature flag '$featureFlagId' has no value")
        }
      }
      .awaitAsListResult()
      .map { it.toMap() }

  override suspend fun <T : FeatureFlagValue> getFlag(
    featureFlagId: String,
    kClass: KClass<T>,
  ): Result<T?, DbError> {
    return when (kClass) {
      BooleanFlag::class ->
        databaseProvider.database()
          .booleanFeatureFlagQueries
          .getFlag(featureFlagId)
          .awaitAsOneOrNullResult()
          .map { getFlagValue ->
            getFlagValue?.let {
              @Suppress("UNCHECKED_CAST")
              BooleanFlag(value = it) as T
            }
          }
      FeatureFlagValue.DoubleFlag::class ->
        databaseProvider.database()
          .doubleFeatureFlagQueries
          .getFlag(featureFlagId)
          .awaitAsOneOrNullResult()
          .map { getFlagValue ->
            getFlagValue?.let {
              @Suppress("UNCHECKED_CAST")
              FeatureFlagValue.DoubleFlag(value = it) as T
            }
          }
      FeatureFlagValue.StringFlag::class ->
        databaseProvider.database()
          .stringFeatureFlagQueries
          .getFlag(featureFlagId)
          .awaitAsOneOrNullResult()
          .map { getFlagValue ->
            getFlagValue?.let {
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
    return databaseProvider.database().awaitTransaction {
      when (flagValue) {
        is BooleanFlag -> {
          doubleFeatureFlagQueries.deleteFlag(featureFlagId)
          stringFeatureFlagQueries.deleteFlag(featureFlagId)
          booleanFeatureFlagQueries.setFlag(featureFlagId, flagValue.value)
        }
        is FeatureFlagValue.DoubleFlag -> {
          booleanFeatureFlagQueries.deleteFlag(featureFlagId)
          stringFeatureFlagQueries.deleteFlag(featureFlagId)
          doubleFeatureFlagQueries.setFlag(featureFlagId, flagValue.value)
        }
        is FeatureFlagValue.StringFlag -> {
          booleanFeatureFlagQueries.deleteFlag(featureFlagId)
          doubleFeatureFlagQueries.deleteFlag(featureFlagId)
          stringFeatureFlagQueries.setFlag(featureFlagId, flagValue.value)
        }
      }
    }
  }

  override suspend fun getFlagOverridden(featureFlagId: String): Result<Boolean, DbError> =
    databaseProvider.database()
      .featureFlagOverrideQueries
      .getFlagOverridden(featureFlagId)
      .awaitAsOneOrNullResult()
      .map { it ?: false }

  override suspend fun setFlagOverridden(
    featureFlagId: String,
    overridden: Boolean,
  ): Result<Unit, DbError> =
    databaseProvider.database()
      .featureFlagOverrideQueries
      .awaitTransaction {
        setFlagOverridden(featureFlagId, overridden)
      }
}
