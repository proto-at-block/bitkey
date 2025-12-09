package bitkey.verification

import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class TxVerificationDaoFake : TxVerificationDao {
  val activePolicy: MutableStateFlow<TxVerificationPolicy.Active?> = MutableStateFlow(null)

  override suspend fun setEnabledThreshold(
    threshold: VerificationThreshold.Enabled,
  ): Result<Unit, Error> {
    return Ok(
      Unit
    ).also {
      activePolicy.value = TxVerificationPolicy.Active(threshold)
    }
  }

  override suspend fun getActivePolicy(): Flow<Result<TxVerificationPolicy.Active?, Error>> {
    return activePolicy.map { Ok(it) }
  }

  override suspend fun deletePolicy(): Result<Unit, DbError> {
    activePolicy.value = null
    return Ok(Unit)
  }
}
