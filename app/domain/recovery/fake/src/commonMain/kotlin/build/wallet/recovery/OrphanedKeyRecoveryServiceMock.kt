package build.wallet.recovery

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.keybox.Keybox
import build.wallet.recovery.OrphanedKeyRecoveryService.RecoverableAccount
import build.wallet.recovery.OrphanedKeyRecoveryService.RecoveryError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result

class OrphanedKeyRecoveryServiceMock(
  turbine: (String) -> Turbine<Any>,
) : OrphanedKeyRecoveryService {
  val canAttemptRecoveryCalls = turbine("can attempt recovery calls")
  val discoverRecoverableAccountsCalls = turbine("discover recoverable accounts calls")
  val recoverFromRecoverableAccountCalls = turbine("recover from recoverable account calls")

  var canAttemptRecoveryResult: Boolean = false
  var discoverRecoverableAccountsResult: Result<List<RecoverableAccount>, RecoveryError> =
    Err(RecoveryError.NoAuthKeyFound)
  var recoverFromRecoverableAccountResult: Result<Keybox, RecoveryError> =
    Err(RecoveryError.NoAuthKeyFound)

  override suspend fun canAttemptRecovery(
    orphanedKeys: List<KeychainScanner.KeychainEntry>,
  ): Boolean {
    canAttemptRecoveryCalls += orphanedKeys
    return canAttemptRecoveryResult
  }

  override suspend fun discoverRecoverableAccounts(): Result<List<RecoverableAccount>, RecoveryError> {
    discoverRecoverableAccountsCalls += Unit
    return discoverRecoverableAccountsResult
  }

  override suspend fun recoverFromRecoverableAccount(
    account: RecoverableAccount,
  ): Result<Keybox, RecoveryError> {
    recoverFromRecoverableAccountCalls += account
    return recoverFromRecoverableAccountResult
  }

  fun reset() {
    canAttemptRecoveryResult = false
    discoverRecoverableAccountsResult = Err(RecoveryError.NoAuthKeyFound)
    recoverFromRecoverableAccountResult = Err(RecoveryError.NoAuthKeyFound)
  }
}
