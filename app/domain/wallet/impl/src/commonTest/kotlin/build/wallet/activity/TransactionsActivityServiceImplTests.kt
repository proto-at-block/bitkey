package build.wallet.activity

import app.cash.turbine.test
import build.wallet.account.AccountServiceFake
import build.wallet.balance.utils.MockPriceDataGenerator
import build.wallet.balance.utils.MockScenarioServiceImpl
import build.wallet.balance.utils.MockTransactionDataGenerator
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderMock
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Outgoing
import build.wallet.bitcoin.transactions.BitcoinTransactionMock
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitcoin.wallet.WatchingWalletMock
import build.wallet.bitcoin.wallet.WatchingWalletProviderMock
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.coroutines.turbine.awaitUntilNotNull
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.recovery.ListKeysetsF8eClientMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.ExpectedTransactionsPhase2FeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.partnerships.*
import build.wallet.partnerships.PartnershipTransactionStatus.PENDING
import build.wallet.partnerships.PartnershipTransactionStatus.SUCCESS
import build.wallet.platform.config.AppVariant
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.store.KeyValueStoreFactoryFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class TransactionsActivityServiceImplTests : FunSpec({

  val partnershipTransactionsService = PartnershipTransactionsServiceMock(
    clearCalls = turbines.create("clear calls"),
    syncCalls = turbines.create("sync calls"),
    createCalls = turbines.create("create calls"),
    fetchMostRecentCalls = turbines.create("fetch most recent calls"),
    updateRecentTransactionStatusCalls = turbines.create("update recent transaction status calls"),
    getCalls = turbines.create("get transaction by id calls")
  )
  val bitcoinWalletService = BitcoinWalletServiceFake()
  val accountService = AccountServiceFake()
  val listKeysetsF8eClient = ListKeysetsF8eClientMock()
  val uuidGenerator = UuidGeneratorFake()

  val featureFlag = ExpectedTransactionsPhase2FeatureFlag(FeatureFlagDaoFake())
  lateinit var service: TransactionsActivityServiceImpl

  val wallet = SpendingWalletMock(turbines::create)
  val watchingWalletMock = WatchingWalletMock()
  val watchingWalletProvider = WatchingWalletProviderMock(watchingWalletMock)

  fun createMockScenarioService() =
    MockScenarioServiceImpl(
      appVariant = AppVariant.Development,
      keyValueStoreFactory = KeyValueStoreFactoryFake(),
      clock = Clock.System,
      mockTransactionDataGenerator = MockTransactionDataGenerator(Clock.System),
      mockPriceDataGenerator = MockPriceDataGenerator(Clock.System),
      coroutineScope = TestScope()
    )

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

  val partnershipTxWithoutStatus = partnershipTxWithMatch.copy(
    id = PartnershipTransactionId("no-status-transaction"),
    txid = "no-status",
    cryptoAmount = 0.00001,
    status = null,
    type = PartnershipTransactionType.TRANSFER
  )

  beforeTest {
    listKeysetsF8eClient.reset()
    partnershipTransactionsService.reset()
    accountService.reset()
    bitcoinWalletService.reset()
    featureFlag.reset()
    watchingWalletProvider.reset()

    bitcoinWalletService.spendingWallet.value = wallet
    bitcoinWalletService.setTransactions(
      listOf(btcTxWithMatch)
    )
    partnershipTransactionsService.transactions.value =
      listOf(partnershipTxWithoutMatch, partnershipTxWithMatch, partnershipTxWithoutStatus)

    featureFlag.setFlagValue(true)

    service = TransactionsActivityServiceImpl(
      expectedTransactionsPhase2FeatureFlag = featureFlag,
      partnershipTransactionsService = partnershipTransactionsService,
      bitcoinWalletService = bitcoinWalletService,
      accountService = accountService,
      watchingWalletProvider = watchingWalletProvider,
      bitcoinMultiSigDescriptorBuilder = BitcoinMultiSigDescriptorBuilderMock(),
      listKeysetsF8eClient = listKeysetsF8eClient,
      appScope = TestScope(),
      mockScenarioService = createMockScenarioService(),
      uuidGenerator = uuidGenerator
    )
  }

  test("sync") {
    service.sync()
    partnershipTransactionsService.syncCalls.awaitItem()
    wallet.syncCalls.awaitItem()
  }

  test("executeWork populates transactions cache") {
    createBackgroundScope().launch {
      service.executeWork()
    }

    service.transactions.test {
      awaitUntilNotNull().shouldContainExactly(
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
      awaitItem().shouldContainExactly(
        Transaction.PartnershipTransaction(
          details = partnershipTxWithMatch,
          bitcoinTransaction = null
        )
      )
    }
  }

  test("transactionById returns a flow of single transaction") {
    createBackgroundScope().launch {
      service.executeWork()
    }

    service.transactionById(partnershipTxWithMatch.id.value).test {
      awaitUntil(
        Transaction.PartnershipTransaction(
          details = partnershipTxWithMatch,
          bitcoinTransaction = btcTxWithMatch
        )
      )
    }
  }

  test("transactionById returns null when transaction is not found") {
    createBackgroundScope().launch {
      service.executeWork()
    }

    service.transactionById("not-found").test {
      awaitItem().shouldBe(null)
    }
  }
})
