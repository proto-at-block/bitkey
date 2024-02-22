package build.wallet.f8e.featureflags

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class GetFeatureFlagsServiceMock : GetFeatureFlagsService {
  private var featureFlagMock =
    GetFeatureFlagsService.F8eFeatureFlag(
      key = "silly-mode-enabled",
      name = "Silly Mode Enabled",
      description = "Controls whether or not the app is silly.",
      value = F8eFeatureFlagValue.BooleanValue(true)
    )

  private var getFeatureFlagsResult:
    Result<List<GetFeatureFlagsService.F8eFeatureFlag>, NetworkingError> =
    Ok(listOf(featureFlagMock))

  override suspend fun getF8eFeatureFlags(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId?,
  ): Result<List<GetFeatureFlagsService.F8eFeatureFlag>, NetworkingError> {
    return getFeatureFlagsResult
  }
}
