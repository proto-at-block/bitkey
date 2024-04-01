package build.wallet.f8e.recovery

import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Result

interface RotateAuthKeysService {
  /**
   * Used to rotate the keyset when we need to replace the auth key during Cloud Recovery
   *
   * @property keysetId This is the keyset that was created with a new auth key
   * @property signature A signature used to confirm that the new auth key is respected on the client
   */
  suspend fun rotateKeyset(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    oldAppAuthPublicKey: PublicKey<AppGlobalAuthKey>,
    newAppAuthPublicKeys: AppAuthPublicKeys,
    hwAuthPublicKey: HwAuthPublicKey,
    hwSignedAccountId: String,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, Throwable>
}
