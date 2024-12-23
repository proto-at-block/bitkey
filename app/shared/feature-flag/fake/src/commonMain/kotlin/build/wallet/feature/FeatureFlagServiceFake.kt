package build.wallet.feature

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class FeatureFlagServiceFake : FeatureFlagService {
  private val dao = FeatureFlagDaoFake()
  override val flagsInitialized = MutableStateFlow<Boolean>(false)

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

  override fun initComposeUiFeatureFlag() = Unit

  suspend fun reset() {
    flagsInitialized.value = false
    resetFlags()
    dao.reset()
  }
}

class DoubleMobileTestFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.DoubleFlag>(
    identifier = "mobile-test-flag-double",
    title = "Double Mobile Test Feature Flag",
    description = "This is a test flag with a Number type",
    defaultFlagValue = FeatureFlagValue.DoubleFlag(0.0),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.DoubleFlag::class
  )

class StringFlagMobileTestFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.StringFlag>(
    identifier = "mobile-test-flag-string",
    title = "String Mobile Test Feature Flag",
    description = "This is a test flag with a String type",
    defaultFlagValue = FeatureFlagValue.StringFlag(""),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.StringFlag::class
  )
