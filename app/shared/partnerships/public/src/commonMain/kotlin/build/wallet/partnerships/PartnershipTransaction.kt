package build.wallet.partnerships

import build.wallet.money.currency.code.IsoCurrencyTextCode
import kotlinx.datetime.Instant

/**
 * Represents known details about an external partnership transaction.
 *
 * A "partnership transaction" refers to purchase or transfer that occurs
 * through one of our partner exchanges. This transaction is initiated on
 * the partner's platform and is expected to show in the wallet's transaction
 * history in the near future, though may not be immediately available.
 * This info is used to track transactions after they are requested from
 * a partner, but before they appear in the wallet's transaction history.
 */
data class PartnershipTransaction(
  val id: PartnershipTransactionId,
  val type: PartnershipTransactionType,
  val status: PartnershipTransactionStatus?,
  val context: String?,
  val partnerInfo: PartnerInfo,
  val cryptoAmount: Double?,
  val txid: String?,
  val fiatAmount: Double?,
  val fiatCurrency: IsoCurrencyTextCode?,
  val paymentMethod: String?,
  val created: Instant,
  val updated: Instant,
)
