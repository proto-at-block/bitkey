package bitkey.verification

import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class TxVerificationDaoFake : TxVerificationDao {
  val activePolicy: MutableStateFlow<TxVerificationPolicy.Active?> = MutableStateFlow(null)

  override suspend fun setActivePolicy(
    txVerificationPolicy: TxVerificationPolicy.Active,
  ): Result<TxVerificationPolicy.Active, Error> {
    return Ok(
      txVerificationPolicy
    ).also {
      activePolicy.value = it.value
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
