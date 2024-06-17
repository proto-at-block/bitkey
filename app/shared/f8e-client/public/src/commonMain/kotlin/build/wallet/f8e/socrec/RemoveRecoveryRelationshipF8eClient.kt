package build.wallet.f8e.socrec

import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface RemoveRecoveryRelationshipF8eClient {
  /**
   * Removes a recovery relationship that the caller is part of.
   *
   * [hardwareProofOfPossession] and [AuthTokenScope.Global] are required for a customer to
   * remove a Trusted Contact.
   *
   * Otherwise, for a Trusted Contact to remove themselves, [AuthTokenScope.Recovery] is
   * expected.
   */
  suspend fun removeRelationship(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
    authTokenScope: AuthTokenScope,
    relationshipId: String,
  ): Result<Unit, NetworkingError>
}
