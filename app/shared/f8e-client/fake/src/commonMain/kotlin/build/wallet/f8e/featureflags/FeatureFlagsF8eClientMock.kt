package build.wallet.f8e.featureflags

import app.cash.turbine.Turbine
import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map

class FeatureFlagsF8eClientMock(
  featureFlags: List<FeatureFlagsF8eClient.F8eFeatureFlag>,
  turbine: (String) -> Turbine<Any>,
) : FeatureFlagsF8eClient {
  val getFeatureFlagsCalls = turbine("get feature flags calls")

  private var getFeatureFlagsResult:
    Result<List<FeatureFlagsF8eClient.F8eFeatureFlag>, NetworkingError> =
    Ok(featureFlags)

  fun setFlags(featureFlags: List<FeatureFlagsF8eClient.F8eFeatureFlag>) {
    getFeatureFlagsResult = Ok(featureFlags)
  }

  override suspend fun getF8eFeatureFlags(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId?,
    flagKeys: List<String>,
  ): Result<List<FeatureFlagsF8eClient.F8eFeatureFlag>, NetworkingError> {
    getFeatureFlagsCalls.add(Unit)

    return getFeatureFlagsResult
      .map { it.filter { flag -> flagKeys.contains(flag.key) } }
  }
}
