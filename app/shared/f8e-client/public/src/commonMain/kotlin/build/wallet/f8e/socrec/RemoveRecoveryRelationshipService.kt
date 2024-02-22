package build.wallet.f8e.socrec

import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.account.Account
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface RemoveRecoveryRelationshipService {
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
    account: Account,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
    authTokenScope: AuthTokenScope,
    relationshipId: String,
  ): Result<Unit, NetworkingError>
}
