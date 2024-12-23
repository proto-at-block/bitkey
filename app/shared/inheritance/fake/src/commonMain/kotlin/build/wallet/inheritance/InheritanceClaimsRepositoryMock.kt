package build.wallet.inheritance

import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.inheritance.InheritanceClaims
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class InheritanceClaimsRepositoryMock(
  private val fetchClaimsResult: Result<InheritanceClaims, Error> = Ok(InheritanceClaims.EMPTY),
) : InheritanceClaimsRepository {
  override val claims = MutableStateFlow<Result<InheritanceClaims, Error>>(Ok(InheritanceClaims.EMPTY))

  override suspend fun fetchClaims(): Result<InheritanceClaims, Error> {
    return fetchClaimsResult
  }

  override suspend fun updateSingleClaim(claim: BeneficiaryClaim) {}
}
