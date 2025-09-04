package build.wallet.f8e.onboarding

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CreateAccountClientErrorCode
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.KeyCrossDraft
import com.github.michaelbull.result.Result

interface CreatePrivateFullAccountF8eClient {
  /**
   * Creates a private (descriptor privacy) [FullAccountId] with f8e
   * Requires app and hardware public keys.
   */
  suspend fun createPrivateAccount(
    keyCrossDraft: KeyCrossDraft.WithAppKeysAndHardwareKeys,
  ): Result<CreateFullAccountF8eClient.Success, F8eError<CreateAccountClientErrorCode>>
}
