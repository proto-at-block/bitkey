package build.wallet.f8e.socrec

import build.wallet.bitkey.account.Account
import build.wallet.bitkey.socrec.Invitation
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.RetrieveTrustedContactInvitationErrorCode
import com.github.michaelbull.result.Result

interface RetrieveTrustedContactInvitationService {
  /**
   * Retrieves invitation data for a potential Trusted Contact given a code.
   * Note: [Account] can be for either a Full or Lite Customer
   */
  suspend fun retrieveInvitation(
    account: Account,
    invitationCode: String,
  ): Result<Invitation, F8eError<RetrieveTrustedContactInvitationErrorCode>>
}
