package build.wallet.f8e.recovery

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class RotateKeysetServiceMock(
  val turbine: (String) -> Turbine<Any>,
) : RotateAuthKeysService {
  val rotateKeysetCalls = turbine("rotateKeyset calls")

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
    return Ok(Unit)
  }
}
