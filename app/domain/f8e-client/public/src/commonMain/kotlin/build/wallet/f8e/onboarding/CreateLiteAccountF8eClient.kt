package build.wallet.f8e.onboarding

import bitkey.account.LiteAccountConfig
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CreateAccountClientErrorCode
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.f8e.LiteAccountId
import build.wallet.crypto.PublicKey
import com.github.michaelbull.result.Result

interface CreateLiteAccountF8eClient {
  /**
   * Creates a [LiteAccountId] with f8e.
   */
  suspend fun createAccount(
    recoveryKey: PublicKey<AppRecoveryAuthKey>,
    config: LiteAccountConfig,
  ): Result<LiteAccountId, F8eError<CreateAccountClientErrorCode>>
}
