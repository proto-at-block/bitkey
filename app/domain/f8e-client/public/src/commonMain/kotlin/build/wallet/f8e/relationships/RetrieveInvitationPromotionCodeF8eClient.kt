package build.wallet.f8e.relationships

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.F8eClientErrorCode
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.promotions.PromotionCode
import com.github.michaelbull.result.Result

interface RetrieveInvitationPromotionCodeF8eClient {
  /**
   * Retrieves any existing promotion code for the account given an invitation code.
   * Note: [Account] can be for either a Full or Lite Customer
   */
  suspend fun retrieveInvitationPromotionCode(
    account: Account,
    invitationCode: String,
  ): Result<PromotionCode?, F8eError<F8eClientErrorCode>>
}
