package build.wallet.f8e.featureflags

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.featureflags.FeatureFlagsF8eClient.F8eFeatureFlag
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class FeatureFlagsF8eClientFake : FeatureFlagsF8eClient {
  private var f8eFlags = emptyList<F8eFeatureFlag>()

  fun setFlags(flags: List<F8eFeatureFlag>) {
    f8eFlags = flags
  }

  override suspend fun getF8eFeatureFlags(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId?,
    flagKeys: List<String>,
  ): Result<List<F8eFeatureFlag>, NetworkingError> {
    return f8eFlags
      .filter { it.key in flagKeys }
      .let { Ok(it) }
  }

  fun reset() {
    f8eFlags = emptyList()
  }
}
