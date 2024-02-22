package build.wallet.f8e.socrec

import build.wallet.bitkey.account.Account
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.AcceptTrustedContactInvitationErrorCode
import com.github.michaelbull.result.Result

interface AcceptTrustedContactInvitationService {
  /**
   * Redeems an invitation to make the caller a trusted contact.
   * Returns the [ProtectedCustomer] that the current customer is now protecting.
   */
  suspend fun acceptInvitation(
    account: Account,
    invitation: Invitation,
    protectedCustomerAlias: ProtectedCustomerAlias,
    trustedContactIdentityKey: TrustedContactIdentityKey,
  ): Result<ProtectedCustomer, F8eError<AcceptTrustedContactInvitationErrorCode>>
}
