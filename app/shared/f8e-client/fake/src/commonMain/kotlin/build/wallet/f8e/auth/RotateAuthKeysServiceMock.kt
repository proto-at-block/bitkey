package build.wallet.f8e.auth

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.recovery.RotateAuthKeysService
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class RotateAuthKeysServiceMock(
  val turbine: (String) -> Turbine<Any>,
) : RotateAuthKeysService {
  val rotateKeysetCalls = turbine("rotateKeyset calls")
  var rotateKeysetResult: Result<Unit, NetworkingError> = Ok(Unit)

  override suspend fun rotateKeyset(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    oldAppAuthPublicKey: PublicKey<AppGlobalAuthKey>,
    newAppAuthPublicKeys: AppAuthPublicKeys,
    hwAuthPublicKey: HwAuthPublicKey,
    hwSignedAccountId: String,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, NetworkingError> {
    rotateKeysetCalls += Unit
    return rotateKeysetResult
  }

  fun reset() {
    rotateKeysetResult = Ok(Unit)
  }
}
