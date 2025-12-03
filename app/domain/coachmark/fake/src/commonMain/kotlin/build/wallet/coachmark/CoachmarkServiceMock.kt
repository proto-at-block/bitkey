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
  private val displayedCoachmarks = mutableSetOf<CoachmarkIdentifier>()

  override suspend fun coachmarksToDisplay(
    coachmarkIds: Set<CoachmarkIdentifier>,
  ): Result<List<CoachmarkIdentifier>, Error> =
    Ok(defaultCoachmarks.filter { it !in displayedCoachmarks })

  override suspend fun markCoachmarkAsDisplayed(
    coachmarkId: CoachmarkIdentifier,
  ): Result<Unit, Error> {
    displayedCoachmarks.add(coachmarkId)
    markDisplayedTurbine += coachmarkId
    return Ok(Unit)
  }

  override suspend fun resetCoachmarks(): Result<Unit, Error> {
    defaultCoachmarks = emptyList()
    displayedCoachmarks.clear()
    return Ok(Unit)
  }

  fun reset() {
    displayedCoachmarks.clear()
    defaultCoachmarks = emptyList()
  }
}
