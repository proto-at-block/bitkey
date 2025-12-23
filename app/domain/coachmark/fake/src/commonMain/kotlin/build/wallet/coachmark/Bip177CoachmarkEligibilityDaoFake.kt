package build.wallet.coachmark

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class Bip177CoachmarkEligibilityDaoFake : Bip177CoachmarkEligibilityDao {
  private var eligibility: Boolean? = null

  override suspend fun getEligibility(): Result<Boolean?, Error> = Ok(eligibility)

  override suspend fun setEligibility(eligible: Boolean): Result<Unit, Error> {
    eligibility = eligible
    return Ok(Unit)
  }

  fun reset() {
    eligibility = null
  }
}
