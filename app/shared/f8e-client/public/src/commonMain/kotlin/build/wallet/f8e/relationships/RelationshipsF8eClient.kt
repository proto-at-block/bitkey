package build.wallet.f8e.relationships

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.ProtectedCustomerEnrollmentPakeKey
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.crypto.PublicKey
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

/**
 * A client for creating relationships between accounts, used for social recovery and inheritance
 */
interface RelationshipsF8eClient {
  /**
   * Creates a relationship between the given account and a trusted contact, which generates an invite
   * to be accepted
   *
   * @param account the account to create the relationship for
   * @param hardwareProofOfPossession the current active hardware proof of possession
   * @param trustedContactAlias the alias of the trusted contact to create the relationship with
   * @param protectedCustomerEnrollmentPakeKey the protected customer enrollment pake key
   * @param roles the roles to assign to the trusted contact
   */
  suspend fun createRelationship(
    account: FullAccount,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    trustedContactAlias: TrustedContactAlias,
    protectedCustomerEnrollmentPakeKey: PublicKey<ProtectedCustomerEnrollmentPakeKey>,
    roles: Set<TrustedContactRole>,
  ): Result<Invitation, NetworkingError>
}
