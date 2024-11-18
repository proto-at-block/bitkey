package build.wallet.feature

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlin.reflect.KClass

class FeatureFlagDaoFake : FeatureFlagDao {
  private val flags = mutableMapOf<String, FeatureFlagValue>()

  override suspend fun <T : FeatureFlagValue> getFlag(
    featureFlagId: String,
    kClass: KClass<T>,
  ): Result<T?, Error> {
    @Suppress("UNCHECKED_CAST")
    return Ok(flags[featureFlagId] as T?)
  }

  override suspend fun <T : FeatureFlagValue> setFlag(
    flagValue: T,
    featureFlagId: String,
  ): Result<Unit, Error> {
    flags[featureFlagId] = flagValue
    return Ok(Unit)
  }

  override suspend fun getFlagOverridden(featureFlagId: String): Result<Boolean, Error> = Ok(false)

  override suspend fun setFlagOverridden(
    featureFlagId: String,
    overridden: Boolean,
  ): Result<Unit, Error> = Ok(Unit)

  fun reset() {
    flags.clear()
  }
}
