package build.wallet.f8e.recovery

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.AuthenticationService
import build.wallet.f8e.recovery.InitiateHardwareAuthService.AuthChallenge
import build.wallet.ktor.result.NetworkingError
import build.wallet.logging.logNetworkFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map

class InitiateHardwareAuthServiceImpl(
  private val authenticationService: AuthenticationService,
) : InitiateHardwareAuthService {
  override suspend fun start(
    f8eEnvironment: F8eEnvironment,
    currentHardwareAuthKey: HwAuthPublicKey,
  ): Result<AuthChallenge, NetworkingError> {
    return authenticationService.initiateAuthentication(
      f8eEnvironment,
      currentHardwareAuthKey
    ).map { body ->
      AuthChallenge(FullAccountId(body.accountId), body.challenge, body.session)
    }
      .logNetworkFailure { "Failed to auth with hardware key on f8e" }
  }
}
