package build.wallet.f8e.inheritance

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.inheritance.InheritanceClaims
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class RetrieveInheritanceClaimsF8EClientFake(
  var response: Result<InheritanceClaims, Error> = Ok(InheritanceClaims.EMPTY),
) : RetrieveInheritanceClaimsF8eClient {
  override suspend fun retrieveInheritanceClaims(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ) = response

  fun reset() {
    response = Ok(InheritanceClaims.EMPTY)
  }
}
