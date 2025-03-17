package build.wallet.cloud.backup

import build.wallet.bitkey.account.LiteAccount
import com.github.michaelbull.result.Result

interface LiteAccountCloudBackupCreator {
  suspend fun create(
    account: LiteAccount,
  ): Result<CloudBackupV2, LiteAccountCloudBackupCreatorError>

  sealed class LiteAccountCloudBackupCreatorError : Error() {
    /** Error while retrieving socrec keys. */
    data class SocRecKeysRetrievalError(
      override val cause: Throwable?,
    ) : LiteAccountCloudBackupCreatorError()

    /** Error while retrieving app recovery auth keypair. */
    data class AppRecoveryAuthKeypairRetrievalError(
      override val cause: Throwable?,
    ) : LiteAccountCloudBackupCreatorError()
  }
}
