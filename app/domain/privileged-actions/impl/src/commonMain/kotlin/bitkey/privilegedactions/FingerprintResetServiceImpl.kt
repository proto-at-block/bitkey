package bitkey.privilegedactions

import bitkey.f8e.fingerprintreset.FingerprintResetF8eClient
import bitkey.f8e.fingerprintreset.FingerprintResetRequest
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import build.wallet.account.AccountService
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.grants.GrantAction
import build.wallet.grants.GrantRequest
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import okio.ByteString.Companion.toByteString

@BitkeyInject(AppScope::class)
class FingerprintResetServiceImpl(
  override val privilegedActionF8eClient: FingerprintResetF8eClient,
  override val accountService: AccountService,
) : FingerprintResetService {
  /**
   * Create a fingerprint reset privileged action using a GrantRequest
   */
  override suspend fun createFingerprintResetPrivilegedAction(
    hwAuthPublicKey: HwAuthPublicKey,
    grantRequest: GrantRequest,
  ): Result<PrivilegedActionInstance, PrivilegedActionError> {
    if (grantRequest.action != GrantAction.FINGERPRINT_RESET) {
      return Err(PrivilegedActionError.UnsupportedActionType)
    }

    val request = FingerprintResetRequest(
      version = grantRequest.version.toInt(),
      action = grantRequest.action.value,
      deviceId = grantRequest.deviceId.decodeToString(),
      challenge = grantRequest.challenge.toIntList(),
      signature = grantRequest.signature.toByteString().hex(),
      hwAuthPublicKey = hwAuthPublicKey.pubKey.value
    )

    return createAction(request)
  }
}

private fun ByteArray.toIntList(): List<Int> = this.map { it.toInt() and 0xFF }
