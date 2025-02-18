package build.wallet.coachmark

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class CoachmarkServiceMock(
  var defaultCoachmarks: List<CoachmarkIdentifier> = emptyList(),
  turbineFactory: (String) -> Turbine<CoachmarkIdentifier>,
) : CoachmarkService {
  val markDisplayedTurbine = turbineFactory("mark coachmark displayed calls")

  override suspend fun coachmarksToDisplay(
    coachmarkIds: Set<CoachmarkIdentifier>,
  ): Result<List<CoachmarkIdentifier>, Error> = Ok(defaultCoachmarks)

  override suspend fun markCoachmarkAsDisplayed(
    coachmarkId: CoachmarkIdentifier,
  ): Result<Unit, Error> =
    Ok(Unit)
      .also { markDisplayedTurbine += coachmarkId }

  override suspend fun resetCoachmarks(): Result<Unit, Error> =
    Ok(Unit)
      .also { defaultCoachmarks = emptyList() }
}
