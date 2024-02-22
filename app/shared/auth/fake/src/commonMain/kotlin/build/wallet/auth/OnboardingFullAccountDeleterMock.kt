package build.wallet.auth

import app.cash.turbine.Turbine
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class OnboardingFullAccountDeleterMock(
  turbine: (String) -> Turbine<FullAccountId>,
) : OnboardingFullAccountDeleter {
  val deleteAccountCalls = turbine("deleteAccount calls")
  var returnError: Error? = null

  override suspend fun deleteAccount(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, Error> {
    deleteAccountCalls.add(fullAccountId)
    return returnError?.let { Err(it) } ?: Ok(Unit)
  }

  fun reset() {
    returnError = null
  }
}
