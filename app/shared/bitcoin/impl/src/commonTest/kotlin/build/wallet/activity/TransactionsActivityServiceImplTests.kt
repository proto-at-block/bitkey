package build.wallet.activity

import app.cash.turbine.test
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Outgoing
import build.wallet.bitcoin.transactions.BitcoinTransactionMock
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.ExpectedTransactionsPhase2FeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.partnerships.*
import build.wallet.partnerships.PartnershipTransactionStatus.PENDING
import build.wallet.partnerships.PartnershipTransactionStatus.SUCCESS
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

class TransactionsActivityServiceImplTests : FunSpec({

  coroutineTestScope = true

  val partnershipTransactionsService = PartnershipTransactionsServiceMock(
    clearCalls = turbines.create("clear calls"),
    syncCalls = turbines.create("sync calls"),
    createCalls = turbines.create("create calls"),
    fetchMostRecentCalls = turbines.create("fetch most recent calls"),
    updateRecentTransactionStatusCalls = turbines.create("update recent transaction status calls")
  )
  val bitcoinWalletService = BitcoinWalletServiceFake()

  val featureFlag = ExpectedTransactionsPhase2FeatureFlag(FeatureFlagDaoFake())
  val service = TransactionsActivityServiceImpl(
    expectedTransactionsPhase2FeatureFlag = featureFlag,
    partnershipTransactionsService = partnershipTransactionsService,
    bitcoinWalletService = bitcoinWalletService
  )

  val wallet = SpendingWalletMock(turbines::create)

  val partnershipTxWithMatch = PartnershipTransaction(
    id = PartnershipTransactionId("pending-partnership-with-match"),
    partnerInfo = PartnerInfo(
      partnerId = PartnerId("test-partner"),
      name = "test-partner-name",
      logoUrl = "test-partner-logo-url",
      logoBadgedUrl = "test-partner-logo-badged-url"
    ),
    context = "test-context",
    type = PartnershipTransactionType.PURCHASE,
    status = PENDING,
    cryptoAmount = 1.23,
    txid = "pending-bitcoin-with-txid-match",
    fiatAmount = 3.21,
    fiatCurrency = IsoCurrencyTextCode("USD"),
    paymentMethod = "test-payment-method",
    created = Instant.fromEpochMilliseconds(248),
    updated = Instant.fromEpochMilliseconds(842),
    sellWalletAddress = "test-sell-wallet-address",
    partnerTransactionUrl = "https://fake-partner.com/transaction/test-id"
  )

  val btcTxWithMatch = BitcoinTransactionMock(
    txid = "pending-bitcoin-with-txid-match",
    total = BitcoinMoney.sats(1000),
    transactionType = Outgoing,
    confirmationTime = null
  )

  val partnershipTxWithoutMatch = partnershipTxWithMatch.copy(
    id = PartnershipTransactionId("confirmed-partnership-with-backup-match"),
    txid = "not-a-match",
    cryptoAmount = 0.00001,
    status = SUCCESS,
    type = PartnershipTransactionType.SALE
  )

  beforeTest {
    partnershipTransactionsService.reset()
    bitcoinWalletService.reset()
    featureFlag.reset()

    bitcoinWalletService.spendingWallet.value = wallet
    bitcoinWalletService.setTransactions(
      listOf(btcTxWithMatch)
    )
    partnershipTransactionsService.transactions.value =
      listOf(partnershipTxWithoutMatch, partnershipTxWithMatch)

    featureFlag.setFlagValue(true)
  }

  test("sync") {
    service.sync()
    partnershipTransactionsService.syncCalls.awaitItem()
    wallet.syncCalls.awaitItem()
  }

  test("executeWork populates transactions cache") {
    runTest {
      backgroundScope.launch {
        service.executeWork()
      }

      service.transactions.test {
        awaitItem().shouldContainExactly(
          Transaction.PartnershipTransaction(
            details = partnershipTxWithMatch,
            bitcoinTransaction = btcTxWithMatch
          ),
          Transaction.PartnershipTransaction(
            details = partnershipTxWithoutMatch,
            bitcoinTransaction = null
          )
        )

        bitcoinWalletService.setTransactions(listOf())
        runCurrent()
        awaitItem().shouldContainExactly(
          Transaction.PartnershipTransaction(
            details = partnershipTxWithMatch,
            bitcoinTransaction = null
          ),
          Transaction.PartnershipTransaction(
            details = partnershipTxWithoutMatch,
            bitcoinTransaction = null
          )
        )

        partnershipTransactionsService.transactions.value = listOf(partnershipTxWithMatch)
        runCurrent()
        awaitItem().shouldContainExactly(
          Transaction.PartnershipTransaction(
            details = partnershipTxWithMatch,
            bitcoinTransaction = null
          )
        )
      }
    }
  }
})
