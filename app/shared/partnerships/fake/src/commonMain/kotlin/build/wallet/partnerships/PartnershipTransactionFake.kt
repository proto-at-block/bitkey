package build.wallet.partnerships

import build.wallet.money.currency.USD
import kotlinx.datetime.Instant

val PartnershipTransactionFake = PartnershipTransaction(
  id = PartnershipTransactionId("test-id"),
  partnerInfo = PartnerInfoFake,
  context = "test-context",
  type = PartnershipTransactionType.PURCHASE,
  status = PartnershipTransactionStatus.PENDING,
  cryptoAmount = 1.23,
  txid = "test-transaction-hash",
  fiatAmount = 3.21,
  fiatCurrency = USD.textCode,
  paymentMethod = "test-payment-method",
  created = Instant.fromEpochMilliseconds(248),
  updated = Instant.fromEpochMilliseconds(842),
  sellWalletAddress = "test-sell-wallet-address",
  partnerTransactionUrl = "https://fake-partner.com/transaction/test-id"
)
