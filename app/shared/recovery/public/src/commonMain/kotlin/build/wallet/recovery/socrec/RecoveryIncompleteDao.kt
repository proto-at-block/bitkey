package build.wallet.recovery.socrec

import build.wallet.db.DbError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * A DAO for storing whether or not the user has completed the social recovery flow.
 * If they have dismissed the recovery flow, it will be true until they complete
 * social recovery or dismiss it manually, at which point it will be false.
 */
interface RecoveryIncompleteDao {
  fun recoveryIncomplete(): Flow<Boolean>

  suspend fun setRecoveryIncomplete(incomplete: Boolean): Result<Unit, DbError>
}
