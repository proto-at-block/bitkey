package build.wallet.f8e.onboarding

import build.wallet.bitkey.account.LiteAccountConfig
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.bitkey.f8e.LiteAccountId
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.CreateAccountClientErrorCode
import com.github.michaelbull.result.Result

interface CreateLiteAccountService {
  /**
   * Creates a [LiteAccountId] with f8e.
   */
  suspend fun createAccount(
    recoveryKey: AppRecoveryAuthPublicKey,
    config: LiteAccountConfig,
  ): Result<LiteAccountId, F8eError<CreateAccountClientErrorCode>>
}
