package build.wallet.partnerships

import kotlinx.datetime.Instant

val FakePartnershipTransaction = PartnershipTransaction(
  id = PartnershipTransactionId("fake-id"),
  type = PartnershipTransactionType.PURCHASE,
  status = null,
  context = null,
  partnerInfo = PartnerInfo(
    partnerId = PartnerId("fake-partner"),
    name = "fake-partner-name",
    logoUrl = null
  ),
  cryptoAmount = null,
  txid = null,
  fiatAmount = null,
  fiatCurrency = null,
  paymentMethod = null,
  created = Instant.DISTANT_PAST,
  updated = Instant.DISTANT_PAST
)
