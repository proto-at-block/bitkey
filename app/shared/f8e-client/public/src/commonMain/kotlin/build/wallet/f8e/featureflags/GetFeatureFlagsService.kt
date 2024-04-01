package build.wallet.f8e.featureflags

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result
import kotlinx.serialization.Serializable

interface GetFeatureFlagsService {
  /**
   * Retrieves a list of [F8eFeatureFlag]s.
   */
  suspend fun getF8eFeatureFlags(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId?,
    flagKeys: List<String>,
  ): Result<List<F8eFeatureFlag>, NetworkingError>

  @Serializable
  data class F8eFeatureFlag(
    var key: String,
    var value: F8eFeatureFlagValue,
  )
}
