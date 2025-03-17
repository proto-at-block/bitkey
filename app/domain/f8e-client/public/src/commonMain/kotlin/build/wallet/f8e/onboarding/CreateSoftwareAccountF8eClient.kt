package build.wallet.f8e.onboarding

import bitkey.account.SoftwareAccountConfig
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CreateAccountClientErrorCode
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.f8e.SoftwareAccountId
import build.wallet.crypto.PublicKey
import com.github.michaelbull.result.Result

interface CreateSoftwareAccountF8eClient {
  /**
   * Creates a Software Account in f8e using app auth keys, returning the accounts
   * [SoftwareAccountId].
   */
  suspend fun createAccount(
    authKey: PublicKey<AppGlobalAuthKey>,
    recoveryAuthKey: PublicKey<AppRecoveryAuthKey>,
    accountConfig: SoftwareAccountConfig,
  ): Result<SoftwareAccountId, F8eError<CreateAccountClientErrorCode>>
}
