package build.wallet.recovery

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.challange.SignedChallenge.HardwareSignedChallenge
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.csek.SealedSsek
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class RecoveryAuthCompleterMock(
  turbine: (String) -> Turbine<Any>,
) : RecoveryAuthCompleter {
  val rotateAuthKeysCalls = turbine("rotate auth keys calls")
  var rotateAuthKeysResult: Result<Unit, Throwable> = Err(NotImplementedError())
  var rotateAuthTokenCallResult: Result<Unit, Throwable> = Ok(Unit)

  override suspend fun rotateAuthKeys(
    fullAccountId: FullAccountId,
    hardwareSignedChallenge: HardwareSignedChallenge,
    destinationAppAuthPubKeys: AppAuthPublicKeys,
    sealedCsek: SealedCsek,
    sealedSsek: SealedSsek,
  ): Result<Unit, Throwable> {
    rotateAuthKeysCalls += Unit
    return rotateAuthKeysResult
  }

  override suspend fun rotateAuthTokens(
    fullAccountId: FullAccountId,
    destinationAppAuthPubKeys: AppAuthPublicKeys,
  ): Result<Unit, Throwable> = rotateAuthTokenCallResult
}
