package build.wallet.feature

import build.wallet.db.DbError
import com.github.michaelbull.result.Result
import kotlin.reflect.KClass

/**
 * A persistent store for feature flag values.
 * This store can support reading and writing to multiple different database tables,
 * based on the type of feature flag. The most common feature flag type is a boolean.
 */
interface FeatureFlagDao {
  /** Returns the value for the flag with the given ID. */
  suspend fun <T : FeatureFlagValue> getFlag(
    featureFlagId: String,
    kClass: KClass<T>,
  ): Result<T?, DbError>

  /** Sets the value for the flag with the given ID. */
  suspend fun <T : FeatureFlagValue> setFlag(
    flagValue: T,
    featureFlagId: String,
  ): Result<Unit, DbError>
}
