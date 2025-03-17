package build.wallet.coachmark

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.datetime.Instant

class CoachmarkDaoFake : CoachmarkDao {
  private var coachmarks: List<Coachmark> = emptyList()

  override suspend fun insertCoachmark(
    id: CoachmarkIdentifier,
    expiration: Instant,
  ): Result<Unit, Error> {
    coachmarks = coachmarks + Coachmark(id, false, expiration)
    return Ok(Unit)
  }

  override suspend fun setViewed(id: CoachmarkIdentifier): Result<Unit, Error> {
    coachmarks = coachmarks.map {
      if (it.id == id) {
        it.copy(viewed = true)
      } else {
        it
      }
    }
    return Ok(Unit)
  }

  override suspend fun getCoachmark(id: CoachmarkIdentifier): Result<Coachmark?, Error> =
    Ok(coachmarks.find { it.id == id })

  override suspend fun getAllCoachmarks(): Result<List<Coachmark>, Error> = Ok(coachmarks)

  override suspend fun resetCoachmarks(): Result<Unit, Error> {
    coachmarks = emptyList()
    return Ok(Unit)
  }
}
