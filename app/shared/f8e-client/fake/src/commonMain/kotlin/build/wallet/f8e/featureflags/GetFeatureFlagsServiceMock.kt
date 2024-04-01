package build.wallet.f8e.featureflags

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map

class GetFeatureFlagsServiceMock(
  featureFlags: List<GetFeatureFlagsService.F8eFeatureFlag>,
) : GetFeatureFlagsService {
  private var getFeatureFlagsResult:
    Result<List<GetFeatureFlagsService.F8eFeatureFlag>, NetworkingError> =
    Ok(featureFlags)

  override suspend fun getF8eFeatureFlags(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId?,
    flagKeys: List<String>,
  ): Result<List<GetFeatureFlagsService.F8eFeatureFlag>, NetworkingError> {
    return getFeatureFlagsResult
      .map { it.filter { flag -> flagKeys.contains(flag.key) } }
  }
}
