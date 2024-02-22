package build.wallet.auth

import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.GetAuthKeyRotationAttempt
import build.wallet.db.DbError
import build.wallet.logging.logFailure
import build.wallet.mapResult
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransaction
import build.wallet.unwrapLoadedValue
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

class AuthKeyRotationAttemptDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : AuthKeyRotationAttemptDao {
  private val database by lazy { databaseProvider.database() }

  override fun getAuthKeyRotationAttemptState(): Flow<Result<AuthKeyRotationAttemptDaoState, Throwable>> {
    return database.authKeyRotationAttemptQueries
      .getAuthKeyRotationAttempt()
      .asFlowOfOneOrNull()
      .unwrapLoadedValue()
      .mapResult {
        when (it) {
          null -> AuthKeyRotationAttemptDaoState.NoAttemptInProgress
          else -> {
            if (!it.succeededServerRotation) {
              AuthKeyRotationAttemptDaoState.AuthKeysWritten(
                appAuthPublicKeys = it.appAuthPublicKeys(),
                hwAuthPublicKey = it.destinationHardwareAuthKey
              )
            } else {
              AuthKeyRotationAttemptDaoState.ServerRotationAttemptComplete(
                appAuthPublicKeys = it.appAuthPublicKeys()
              )
            }
          }
        }
      }
      .distinctUntilChanged()
  }

  override suspend fun setAuthKeysWritten(
    appAuthPublicKeys: AppAuthPublicKeys,
    hwAuthPublicKey: HwAuthPublicKey,
  ): Result<Unit, DbError> {
    return database.awaitTransaction {
      authKeyRotationAttemptQueries.setAuthKeyCreated(
        destinationAppGlobalAuthKey = appAuthPublicKeys.appGlobalAuthPublicKey,
        destinationAppRecoveryAuthKey = appAuthPublicKeys.appRecoveryAuthPublicKey,
        destinationHardwareAuthKey = hwAuthPublicKey
      )
    }.logFailure { "Error setting auth keys written." }
  }

  override suspend fun setServerRotationAttemptComplete(): Result<Unit, DbError> {
    return database.awaitTransaction {
      authKeyRotationAttemptQueries.setSucceededServerRotation()
    }.logFailure { "Error setting server rotation attempt complete." }
  }

  override suspend fun clear(): Result<Unit, DbError> {
    return database.awaitTransaction {
      authKeyRotationAttemptQueries.clear()
    }.logFailure { "Error clearing auth key rotation attempt" }
  }

  private fun GetAuthKeyRotationAttempt.appAuthPublicKeys(): AppAuthPublicKeys {
    return AppAuthPublicKeys(
      appGlobalAuthPublicKey = this.destinationAppGlobalAuthKey,
      appRecoveryAuthPublicKey = this.destinationAppRecoveryAuthKey
    )
  }
}
