package build.wallet.f8e.auth

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import bitkey.account.HardwareType
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.f8e.recovery.RotateAuthKeysF8eClient
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class RotateAuthKeysF8eClientMock(
  val turbine: (String) -> Turbine<Any>,
) : RotateAuthKeysF8eClient {
  val rotateKeysetCalls = turbine("rotateKeyset calls")
  var rotateKeysetResult: Result<Unit, NetworkingError> = Ok(Unit)
  var lastRotateKeysetArgs: RotateKeysetArgs? = null

  data class RotateKeysetArgs(
    val fullAccountId: FullAccountId,
    val oldAppAuthPublicKey: PublicKey<AppGlobalAuthKey>,
    val newAppAuthPublicKeys: AppAuthPublicKeys,
    val hwAuthPublicKey: HwAuthPublicKey,
    val hwSignedAccountId: String,
    val proof: PrivilegedActionProof,
  )

  override suspend fun rotateKeyset(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    oldAppAuthPublicKey: PublicKey<AppGlobalAuthKey>,
    newAppAuthPublicKeys: AppAuthPublicKeys,
    hwAuthPublicKey: HwAuthPublicKey,
    hwSignedAccountId: String,
    proof: PrivilegedActionProof,
    hardwareType: HardwareType,
  ): Result<Unit, NetworkingError> {
    rotateKeysetCalls += Unit
    lastRotateKeysetArgs = RotateKeysetArgs(
      fullAccountId = fullAccountId,
      oldAppAuthPublicKey = oldAppAuthPublicKey,
      newAppAuthPublicKeys = newAppAuthPublicKeys,
      hwAuthPublicKey = hwAuthPublicKey,
      hwSignedAccountId = hwSignedAccountId,
      proof = proof
    )
    return rotateKeysetResult
  }

  fun reset() {
    rotateKeysetResult = Ok(Unit)
    lastRotateKeysetArgs = null
  }
}
