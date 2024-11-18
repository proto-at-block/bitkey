package build.wallet.recovery.socrec

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class SocRecStartedChallengeDaoFake : SocRecStartedChallengeDao {
  var pendingChallengeId: String? = null

  override suspend fun get(): Result<String?, Error> {
    return Ok(pendingChallengeId)
  }

  override suspend fun set(challengeId: String): Result<Unit, Error> {
    pendingChallengeId = challengeId
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, Error> {
    pendingChallengeId = null
    return Ok(Unit)
  }
}
