package build.wallet.coachmark

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class CoachmarkServiceMock : CoachmarkService {
  override suspend fun coachmarksToDisplay(
    coachmarkIds: Set<CoachmarkIdentifier>,
  ): Result<List<CoachmarkIdentifier>, Error> = Ok(emptyList())

  override suspend fun markCoachmarkAsDisplayed(
    coachmarkId: CoachmarkIdentifier,
  ): Result<Unit, Error> = Ok(Unit)

  override suspend fun resetCoachmarks(): Result<Unit, Error> = Ok(Unit)
}
