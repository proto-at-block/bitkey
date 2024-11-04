package build.wallet.inheritance

import build.wallet.bitkey.inheritance.BenefactorClaim
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.inheritance.InheritanceClaims
import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class InheritanceClaimsDaoFake : InheritanceClaimsDao {
  override val pendingBeneficiaryClaims = MutableStateFlow<Result<List<BeneficiaryClaim.PendingClaim>, DbError>>(Ok(emptyList()))
  override val pendingBenefactorClaims = MutableStateFlow<Result<List<BenefactorClaim.PendingClaim>, DbError>>(Ok(emptyList()))

  override suspend fun setInheritanceClaims(
    inheritanceClaims: InheritanceClaims,
  ): Result<Unit, DbError> {
    pendingBeneficiaryClaims.value = inheritanceClaims.beneficiaryClaims.filterIsInstance<BeneficiaryClaim.PendingClaim>().let(::Ok)
    pendingBenefactorClaims.value = inheritanceClaims.benefactorClaims.filterIsInstance<BenefactorClaim.PendingClaim>().let(::Ok)
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, DbError> {
    pendingBeneficiaryClaims.value = Ok(emptyList())
    pendingBenefactorClaims.value = Ok(emptyList())
    return Ok(Unit)
  }
}
