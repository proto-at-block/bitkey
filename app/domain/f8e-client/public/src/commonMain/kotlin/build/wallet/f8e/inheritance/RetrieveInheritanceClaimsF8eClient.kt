package build.wallet.f8e.inheritance

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.inheritance.InheritanceClaims
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

interface RetrieveInheritanceClaimsF8eClient {
  /**
   * Retrieves inheritance claims from f8e.
   *
   * For Benefactors, we will show all the inheritance claims for which they are a benefactor
   * For Recovery Contacts, we will show all the inheritance claims for which they are a beneficiary
   */
  suspend fun retrieveInheritanceClaims(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<InheritanceClaims, Error>
}
