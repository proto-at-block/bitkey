package build.wallet.f8e.onboarding

import bitkey.auth.AuthTokenScope
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.platform.config.TouchpointPlatform
import com.github.michaelbull.result.Result

interface AddDeviceTokenF8eClient {
  suspend fun add(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    token: String,
    touchpointPlatform: TouchpointPlatform,
    authTokenScope: AuthTokenScope,
  ): Result<Unit, NetworkingError>
}
