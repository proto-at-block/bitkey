package build.wallet.recovery.socrec

import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class RecoveryIncompleteDaoMock : RecoveryIncompleteDao {
  var recoveryIncomplete = false

  override fun recoveryIncomplete(): Flow<Boolean> {
    return flowOf(recoveryIncomplete)
  }

  override suspend fun setRecoveryIncomplete(incomplete: Boolean): Result<Unit, DbError> {
    recoveryIncomplete = incomplete
    return Ok(Unit)
  }
}
