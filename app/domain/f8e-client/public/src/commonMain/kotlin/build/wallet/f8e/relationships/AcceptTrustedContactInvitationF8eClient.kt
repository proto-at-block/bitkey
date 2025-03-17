package build.wallet.f8e.relationships

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.AcceptTrustedContactInvitationErrorCode
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.relationships.IncomingInvitation
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.bitkey.relationships.ProtectedCustomerAlias
import build.wallet.bitkey.relationships.TrustedContactEnrollmentPakeKey
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.XCiphertext
import com.github.michaelbull.result.Result
import okio.ByteString

interface AcceptTrustedContactInvitationF8eClient {
  /**
   * Redeems an invitation to make the caller a trusted contact.
   * Returns the [ProtectedCustomer] that the current customer is now protecting.
   */
  suspend fun acceptInvitation(
    account: Account,
    invitation: IncomingInvitation,
    protectedCustomerAlias: ProtectedCustomerAlias,
    trustedContactEnrollmentPakeKey: PublicKey<TrustedContactEnrollmentPakeKey>,
    enrollmentPakeConfirmation: ByteString,
    sealedDelegateDecryptionKeyCipherText: XCiphertext,
  ): Result<ProtectedCustomer, F8eError<AcceptTrustedContactInvitationErrorCode>>
}
