package build.wallet.f8e.socrec

import build.wallet.bitkey.account.Account
import build.wallet.bitkey.socrec.IncomingInvitation
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.RetrieveTrustedContactInvitationErrorCode
import com.github.michaelbull.result.Result

interface RetrieveTrustedContactInvitationF8eClient {
  /**
   * Retrieves invitation data for a potential Trusted Contact given a code.
   * Note: [Account] can be for either a Full or Lite Customer
   */
  suspend fun retrieveInvitation(
    account: Account,
    invitationCode: String,
  ): Result<IncomingInvitation, F8eError<RetrieveTrustedContactInvitationErrorCode>>
}
