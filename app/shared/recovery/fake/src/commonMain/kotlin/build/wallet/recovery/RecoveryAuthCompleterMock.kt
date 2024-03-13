package build.wallet.recovery

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result

class RecoveryAuthCompleterMock(
  turbine: (String) -> Turbine<Any>,
) : RecoveryAuthCompleter {
  val rotateAuthKeysCalls = turbine("rotate auth keys calls")
  var rotateAuthKeysResult: Result<Unit, Throwable> =
    Err(NotImplementedError())

  override suspend fun rotateAuthKeys(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    challenge: ChallengeToCompleteRecovery,
    hardwareSignedChallenge: SignedChallengeToCompleteRecovery,
    destinationAppAuthPubKeys: AppAuthPublicKeys,
    sealedCsek: SealedCsek,
    removeProtectedCustomers: Boolean,
  ): Result<Unit, Throwable> {
    rotateAuthKeysCalls += Unit
    return rotateAuthKeysResult
  }
}
