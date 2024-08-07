package build.wallet.feature

import build.wallet.feature.flags.DoubleMobileTestFeatureFlag
import build.wallet.feature.flags.StringFlagMobileTestFeatureFlag
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class FeatureFlagServiceFake : FeatureFlagService {
  private val dao = FeatureFlagDaoFake()
  override val featureFlagsInitialized = MutableStateFlow<Boolean>(false)

  override fun getFeatureFlags(): List<FeatureFlag<out FeatureFlagValue>> {
    return listOf(
      StringFlagMobileTestFeatureFlag(featureFlagDao = dao),
      DoubleMobileTestFeatureFlag(featureFlagDao = dao)
    )
  }

  override suspend fun resetFlags(): Result<Unit, Error> {
    for (flag in getFeatureFlags()) {
      flag.reset()
    }
    return Ok(Unit)
  }

  suspend fun reset() {
    featureFlagsInitialized.value = false
    resetFlags()
    dao.reset()
  }
}
