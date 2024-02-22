package build.wallet.f8e.socrec

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface CreateTrustedContactInvitationService {
  /**
   * Creates an invitation to become the caller’s trusted contact.
   *
   * @param trustedContactAlias: Alias for the TC being invited
   * @param protectedCustomerAlias: Alias of the customer creating the invite
   */
  suspend fun createInvitation(
    account: FullAccount,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    trustedContactAlias: TrustedContactAlias,
  ): Result<Invitation, NetworkingError>
}
