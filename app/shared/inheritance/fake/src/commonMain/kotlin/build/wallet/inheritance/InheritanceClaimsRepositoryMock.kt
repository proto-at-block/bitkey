package build.wallet.inheritance

import app.cash.turbine.Turbine
import build.wallet.bitkey.inheritance.InheritanceClaim
import build.wallet.bitkey.inheritance.InheritanceClaims
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class InheritanceClaimsRepositoryMock(
  val updateSingleClaimCalls: Turbine<InheritanceClaim>,
  var fetchClaimsResult: Result<InheritanceClaims, Error> = Ok(InheritanceClaims.EMPTY),
) : InheritanceClaimsRepository {
  override val claims = MutableStateFlow<Result<InheritanceClaims, Error>>(Ok(InheritanceClaims.EMPTY))

  override suspend fun fetchClaims(): Result<InheritanceClaims, Error> {
    return fetchClaimsResult
  }

  override suspend fun updateSingleClaim(claim: InheritanceClaim) {
    updateSingleClaimCalls.add(claim)
  }
}
