package build.wallet.f8e.partnerships

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnershipTransaction
import build.wallet.partnerships.PartnershipTransactionId
import build.wallet.partnerships.PartnershipTransactionType
import com.github.michaelbull.result.Result

/**
 * Used to look up the status of a partnership transaction.
 *
 * These requests are made to the F8e service, which proxies requests based
 * on the specified partner.
 * For more information about partnership transactions see the
 * [PartnershipTransaction] class.
 */
interface GetPartnershipTransactionF8eClient {
  suspend fun getPartnershipTransaction(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
    partner: PartnerId,
    partnershipTransactionId: PartnershipTransactionId,
    transactionType: PartnershipTransactionType,
  ): Result<F8ePartnershipTransaction, NetworkingError>
}
