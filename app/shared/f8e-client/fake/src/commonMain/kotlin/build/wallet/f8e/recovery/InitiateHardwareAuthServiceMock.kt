package build.wallet.f8e.recovery

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.recovery.InitiateHardwareAuthService.AuthChallenge
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class InitiateHardwareAuthServiceMock(
  turbine: (String) -> Turbine<Any>,
) : InitiateHardwareAuthService {
  var challengeResult: Result<AuthChallenge, NetworkingError> = Ok(AuthChallengeMock)
  val startCalls = turbine("InitiateHardwareAuthServiceMock start calls")

  override suspend fun start(
    f8eEnvironment: F8eEnvironment,
    currentHardwareAuthKey: HwAuthPublicKey,
  ): Result<AuthChallenge, NetworkingError> {
    startCalls += Unit
    return challengeResult
  }

  fun reset() {
    challengeResult = Ok(AuthChallengeMock)
  }
}
