package build.wallet.f8e.relationships

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CreateTrustedContactInvitationErrorCode
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.ProtectedCustomerEnrollmentPakeKey
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.crypto.PublicKey
import build.wallet.f8e.auth.PrivilegedActionProof
import com.github.michaelbull.result.Result

interface CreateTrustedContactInvitationF8eClient {
  /**
   * Creates a relationship between the given account and a trusted contact, which generates an invite
   * to be accepted
   *
   * @param account the account to create the relationship for
   * @param proof authorization proof — either HW proof-of-possession or a signed action proof
   * @param trustedContactAlias the alias of the trusted contact to create the relationship with
   * @param protectedCustomerEnrollmentPakeKey the protected customer enrollment pake key
   * @param roles the roles to assign to the trusted contact
   */
  suspend fun createInvitation(
    account: FullAccount,
    proof: PrivilegedActionProof,
    trustedContactAlias: TrustedContactAlias,
    protectedCustomerEnrollmentPakeKey: PublicKey<ProtectedCustomerEnrollmentPakeKey>,
    roles: Set<TrustedContactRole>,
  ): Result<Invitation, F8eError<CreateTrustedContactInvitationErrorCode>>
}
