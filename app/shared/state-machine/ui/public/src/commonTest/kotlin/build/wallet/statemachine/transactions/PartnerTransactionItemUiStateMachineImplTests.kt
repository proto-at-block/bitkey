package build.wallet.statemachine.transactions

import app.cash.turbine.plusAssign
import build.wallet.activity.Transaction
import build.wallet.bitcoin.BlockTimeFake
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.USD
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryFake
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.partnerships.*
import build.wallet.statemachine.core.test
import build.wallet.time.ClockFake
import build.wallet.time.DateTimeFormatterMock
import build.wallet.time.TimeZoneProviderMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.hours

class PartnerTransactionItemUiStateMachineImplTests : FunSpec({
  val currencyConverter = CurrencyConverterFake()
  val moneyDisplayFormatter = MoneyDisplayFormatterFake
  val dateTimeFormatter = DateTimeFormatterMock()
  val timeZoneProvider = TimeZoneProviderMock()
  val onClickCalls = turbines.create<Unit>("onClick calls")

  val stateMachine = PartnerTransactionItemUiStateMachineImpl(
    currencyConverter = currencyConverter,
    moneyDisplayFormatter = moneyDisplayFormatter,
    dateTimeFormatter = dateTimeFormatter,
    timeZoneProvider = timeZoneProvider,
    fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryFake(USD),
    clock = ClockFake(now = Instant.DISTANT_PAST)
  )

  val baseProps = PartnerTransactionItemUiProps(
    transaction = Transaction.PartnershipTransaction(
      bitcoinTransaction = BitcoinTransaction(
        id = "onchain-id",
        recipientAddress = null,
        broadcastTime = null,
        confirmationStatus = BitcoinTransaction.ConfirmationStatus.Confirmed(BlockTimeFake),
        vsize = null,
        weight = null,
        fee = null,
        subtotal = BitcoinMoney.sats(5000),
        total = BitcoinMoney.sats(6000),
        transactionType = BitcoinTransaction.TransactionType.Incoming,
        estimatedConfirmationTime = null,
        inputs = emptyImmutableList(),
        outputs = emptyImmutableList()
      ),
      details = PartnershipTransaction(
        id = PartnershipTransactionId("partner-id"),
        type = PartnershipTransactionType.PURCHASE,
        status = PartnershipTransactionStatus.SUCCESS,
        context = "",
        partnerInfo = PartnerInfo(
          name = "partner-name",
          logoUrl = "partner-logo-url",
          partnerId = PartnerId("partner-id"),
          logoBadgedUrl = "partner-logo-badged-url"
        ),
        cryptoAmount = 4000.0,
        txid = "txid",
        fiatAmount = 100.0,
        fiatCurrency = IsoCurrencyTextCode("USD"),
        paymentMethod = "CARD",
        created = Instant.DISTANT_PAST,
        updated = Instant.DISTANT_PAST + 5.hours,
        sellWalletAddress = "sell-wallet-address",
        partnerTransactionUrl = "partner-transaction-url"
      )
    ),
    onClick = {
      onClickCalls += Unit
    }
  )

  test("Completed partner transaction") {
    stateMachine.test(baseProps) {
      awaitItem().apply {
        title.shouldBe("Purchase")
        secondaryText.shouldBe("date-time")
        sideText.shouldBe("+ $100.00")
        secondarySideText.shouldBe("5,000 sats")
        onClick.shouldNotBeNull().invoke()
      }

      onClickCalls.awaitItem()
    }
  }
})
