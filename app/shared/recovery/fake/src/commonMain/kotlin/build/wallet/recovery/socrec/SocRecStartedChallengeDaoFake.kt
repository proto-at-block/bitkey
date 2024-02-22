package build.wallet.recovery.socrec

import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class SocRecStartedChallengeDaoFake : SocRecStartedChallengeDao {
  var pendingChallengeId: String? = null

  override suspend fun get(): Result<String?, DbError> {
    return Ok(pendingChallengeId)
  }

  override suspend fun set(challengeId: String): Result<Unit, DbError> {
    pendingChallengeId = challengeId
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, DbError> {
    pendingChallengeId = null
    return Ok(Unit)
  }
}
