package build.wallet.feature

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlin.reflect.KClass

class FeatureFlagDaoMock : FeatureFlagDao {
  override suspend fun <T : FeatureFlagValue> getFlag(
    featureFlagId: String,
    kClass: KClass<T>,
  ): Result<T?, Error> {
    return Ok(null)
  }

  override suspend fun <T : FeatureFlagValue> setFlag(
    flagValue: T,
    featureFlagId: String,
  ): Result<Unit, Error> = Ok(Unit)

  override suspend fun getFlagOverridden(featureFlagId: String): Result<Boolean, Error> = Ok(false)

  override suspend fun setFlagOverridden(
    featureFlagId: String,
    overridden: Boolean,
  ) = Ok(Unit)
}
