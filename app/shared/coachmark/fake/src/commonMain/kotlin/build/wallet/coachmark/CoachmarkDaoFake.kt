package build.wallet.coachmark

import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.datetime.Instant

class CoachmarkDaoFake : CoachmarkDao {
  private var coachmarks: List<Coachmark> = emptyList()

  override suspend fun insertCoachmark(
    id: CoachmarkIdentifier,
    expiration: Instant,
  ): Result<Unit, DbError> {
    coachmarks = coachmarks + Coachmark(id.string, false, expiration)
    return Ok(Unit)
  }

  override suspend fun setViewed(id: CoachmarkIdentifier): Result<Unit, DbError> {
    coachmarks = coachmarks.map {
      if (it.coachmarkId == id.string) {
        it.copy(viewed = true)
      } else {
        it
      }
    }
    return Ok(Unit)
  }

  override suspend fun getCoachmark(id: CoachmarkIdentifier): Result<Coachmark?, DbError> =
    Ok(coachmarks.find { it.coachmarkId == id.string })

  override suspend fun getAllCoachmarks(): Result<List<Coachmark>, DbError> = Ok(coachmarks)

  override suspend fun resetCoachmarks(): Result<Unit, DbError> {
    coachmarks = emptyList()
    return Ok(Unit)
  }
}
