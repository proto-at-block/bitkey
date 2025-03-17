package build.wallet.f8e.partnerships

import build.wallet.partnerships.*

val FakePartnershipTransfer = F8ePartnershipTransaction(
  id = PartnershipTransactionId("fake-transaction-id"),
  type = PartnershipTransactionType.TRANSFER,
  status = PartnershipTransactionStatus.PENDING,
  context = null,
  partnerInfo = PartnerInfo(
    logoUrl = null,
    name = "fake-partner",
    partnerId = PartnerId("fake-partner-id"),
    logoBadgedUrl = null
  ),
  cryptoAmount = null,
  txid = null,
  fiatAmount = null,
  fiatCurrency = null,
  paymentMethod = null,
  sellWalletAddress = "tb1q9lzkpxafkn4fapete0wu8skkux4ccsw5tq8sf6",
  partnerTransactionUrl = "https://fake-partner.com/transaction/fake-transaction-id"
)
