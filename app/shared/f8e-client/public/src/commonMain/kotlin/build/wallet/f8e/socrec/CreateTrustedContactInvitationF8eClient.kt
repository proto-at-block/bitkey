package build.wallet.f8e.socrec

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.ProtectedCustomerEnrollmentPakeKey
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.crypto.PublicKey
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface CreateTrustedContactInvitationF8eClient {
  /**
   * Creates an invitation to become the caller’s trusted contact.
   *
   * @param trustedContactAlias: Alias for the TC being invited
   * @param protectedCustomerAlias: Alias of the customer creating the invite
   * @param protectedCustomerEnrollmentPakeKey: The customer’s enrollment PAKE key
   */
  suspend fun createInvitation(
    account: FullAccount,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    trustedContactAlias: TrustedContactAlias,
    protectedCustomerEnrollmentPakeKey: PublicKey<ProtectedCustomerEnrollmentPakeKey>,
  ): Result<Invitation, NetworkingError>
}
