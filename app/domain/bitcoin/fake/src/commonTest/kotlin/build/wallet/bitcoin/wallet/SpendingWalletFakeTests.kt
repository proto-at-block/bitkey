package build.wallet.bitcoin.wallet

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.balance.BitcoinBalance.Companion.ZeroBalance
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Confirmed
import build.wallet.money.BitcoinMoney
import build.wallet.money.negate
import build.wallet.testing.shouldBeOk
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.time.Duration.Companion.seconds

class SpendingWalletFakeTests : FunSpec({

  val wallet = SpendingWalletFake("fake-wallet")

  afterTest {
    wallet.reset()
  }

  test("receive funds to last unused address") {
    val lastUnusedAddress = wallet.getLastUnusedAddress().shouldBeOk()

    wallet.receiveFunds(BitcoinMoney.sats(5_000))
    wallet.mineBlock()
    wallet.sync().shouldBeOk()

    wallet.transactions().test {
      awaitItem()
        .single()
        .should {
          it.recipientAddress.shouldBe(lastUnusedAddress)
        }
    }
  }

  test("update address after receiving funds") {
    val lastUnusedAddress = wallet.getLastUnusedAddress().shouldBeOk()
    wallet.receiveFunds(BitcoinMoney.sats(5_000))
    wallet.getLastUnusedAddress().shouldBeOk().shouldNotBe(lastUnusedAddress)
  }

  test("do not update address after sending funds") {
    val lastUnusedAddress = wallet.getLastUnusedAddress().shouldBeOk()
    wallet.sendFunds(BitcoinMoney.sats(5_000), BitcoinMoney.sats(500))
    wallet.getLastUnusedAddress().shouldBeOk().shouldBe(lastUnusedAddress)
  }

  test("initial balance and transaction history") {
    turbineScope {
      val balance = wallet.balance().testIn(this)
      val transactions = wallet.transactions().testIn(this)

      withClue("zero balance and empty transaction history after sync") {
        wallet.sync().shouldBeOk()
        balance.awaitItem().shouldBe(ZeroBalance)
        transactions.awaitItem().shouldBeEmpty()
      }

      balance.expectNoEvents()
      balance.cancelAndIgnoreRemainingEvents()
      transactions.expectNoEvents()
      transactions.cancelAndIgnoreRemainingEvents()
    }
  }

  test("receive funds, mine block and sync") {
    turbineScope(timeout = 1.seconds) {
      val receivedAmount = BitcoinMoney.sats(5_000)

      withClue("receive funds and sync") {
        wallet.receiveFunds(receivedAmount)
        wallet.sync().shouldBeOk()
      }

      val balance = wallet.balance().testIn(this, 1.seconds)
      val transactions = wallet.transactions().testIn(this, 1.seconds)

      withClue("should have pending spendable balance") {
        balance.awaitItem().shouldBe(
          BitcoinBalance(
            immature = BitcoinMoney.zero(),
            trustedPending = receivedAmount,
            untrustedPending = BitcoinMoney.zero(),
            confirmed = BitcoinMoney.zero(),
            spendable = receivedAmount,
            total = receivedAmount
          )
        )
      }

      withClue("should have pending incoming transaction") {
        transactions.awaitItem()
          .single()
          .should {
            it.subtotal.shouldBe(receivedAmount)
            it.total.shouldBe(receivedAmount)
            it.shouldBeIncoming()
            it.shouldBePending()
          }
      }

      withClue("mine block and sync") {
        wallet.mineBlock()
        wallet.sync()
      }

      withClue("should have confirmed spendable balance") {
        balance.awaitItem().shouldBe(
          BitcoinBalance(
            immature = BitcoinMoney.zero(),
            trustedPending = BitcoinMoney.zero(),
            untrustedPending = BitcoinMoney.zero(),
            confirmed = receivedAmount,
            spendable = receivedAmount,
            total = receivedAmount
          )
        )
      }

      withClue("should have confirmed incoming transaction") {
        transactions.awaitItem()
          .single()
          .should {
            it.total.shouldBe(receivedAmount)
            it.shouldBeIncoming()
            it.confirmationStatus
              .shouldBeTypeOf<Confirmed>()
              .blockTime.height.shouldBe(401)
          }
      }

      balance.expectNoEvents()
      balance.cancelAndIgnoreRemainingEvents()
      transactions.expectNoEvents()
      transactions.cancelAndIgnoreRemainingEvents()
    }
  }

  test("send funds, mine block and sync") {
    turbineScope {
      val initialBalance = BitcoinMoney.sats(10_000)

      withClue("initial balance") {
        wallet.receiveFunds(initialBalance)
        wallet.mineBlock()
        wallet.sync()
      }

      val fee = BitcoinMoney.sats(500)
      val sentAmount = BitcoinMoney.sats(5_000)
      val totalSentAmount = sentAmount + fee

      withClue("send confirmed tnx and sync") {
        wallet.sendFunds(sentAmount, fee)
        wallet.sync().shouldBeOk()
      }

      val balance = wallet.balance().testIn(this)
      val transactions = wallet.transactions().testIn(this)

      withClue("should have balance with subtracted pending amount") {
        balance.awaitItem().shouldBe(
          BitcoinBalance(
            immature = BitcoinMoney.zero(),
            trustedPending = totalSentAmount.negate(),
            untrustedPending = BitcoinMoney.zero(),
            confirmed = initialBalance,
            spendable = initialBalance - totalSentAmount,
            total = initialBalance - totalSentAmount
          )
        )
      }

      withClue("should have new pending outgoing transaction") {
        val tnxs = transactions.awaitItem()
          .apply { size.shouldBe(2) }

        // initial balance (confirmed)
        tnxs[0]
          .should {
            it.fee.shouldBeNull()
            it.subtotal.shouldBe(initialBalance)
            it.total.shouldBe(initialBalance)
            it.shouldBeIncoming()
            it.confirmationStatus
              .shouldBeTypeOf<Confirmed>()
              .blockTime.height.shouldBe(401)
          }

        // sent (pending)
        tnxs[1]
          .should {
            it.fee.shouldBe(fee)
            it.subtotal.shouldBe(sentAmount)
            it.total.shouldBe(totalSentAmount)
            it.shouldBeOutgoing()
            it.shouldBePending()
          }
      }

      withClue("mine block and sync") {
        wallet.mineBlock()
        wallet.sync()
      }

      withClue("should have balance with subtracted confirmed amount") {
        balance.awaitItem()
          .shouldBe(
            BitcoinBalance(
              immature = BitcoinMoney.zero(),
              trustedPending = BitcoinMoney.zero(),
              untrustedPending = BitcoinMoney.zero(),
              confirmed = initialBalance - totalSentAmount,
              spendable = initialBalance - totalSentAmount,
              total = initialBalance - totalSentAmount
            )
          )
      }

      withClue("should have two outgoing confirmed transactions") {
        val tnxs =
          transactions.awaitItem()
            .apply {
              size.shouldBe(2)
            }

        // initial balance (confirmed)
        tnxs[0]
          .should {
            it.fee.shouldBeNull()
            it.subtotal.shouldBe(initialBalance)
            it.total.shouldBe(initialBalance)
            it.shouldBeIncoming()
            it.confirmationStatus
              .shouldBeTypeOf<Confirmed>()
              .blockTime.height.shouldBe(401)
          }

        // sent (confirmed)
        tnxs[1]
          .should {
            it.fee.shouldBe(fee)
            it.subtotal.shouldBe(sentAmount)
            it.total.shouldBe(totalSentAmount)
            it.shouldBeOutgoing()
            it.confirmationStatus
              .shouldBeTypeOf<Confirmed>()
              .blockTime.height.shouldBe(402)
          }
      }

      balance.expectNoEvents()
      balance.cancelAndIgnoreRemainingEvents()
      transactions.expectNoEvents()
      transactions.cancelAndIgnoreRemainingEvents()
    }
  }

  test("send and receive funds, mine block and sync") {
    turbineScope {
      val initialBalance = BitcoinMoney.sats(10_000)

      withClue("initial balance") {
        wallet.receiveFunds(initialBalance)
        wallet.mineBlock()
        wallet.sync().shouldBeOk()
      }

      val fee = BitcoinMoney.sats(500)
      val sentAmount = BitcoinMoney.sats(5_000)
      val totalSentAmount = sentAmount + fee

      val receivedAmount = BitcoinMoney.sats(2_000)

      withClue("send and receive funds") {
        wallet.sendFunds(sentAmount, fee)
        wallet.receiveFunds(receivedAmount)
        wallet.sync().shouldBeOk()
      }

      val balance = wallet.balance().testIn(this)
      val transactions = wallet.transactions().testIn(this)

      withClue("should have balance with subtracted pending amount and added received amount") {
        balance.awaitItem()
          .shouldBe(
            BitcoinBalance(
              immature = BitcoinMoney.zero(),
              trustedPending = totalSentAmount.negate() + receivedAmount,
              untrustedPending = BitcoinMoney.zero(),
              confirmed = initialBalance,
              spendable = initialBalance - totalSentAmount + receivedAmount,
              total = initialBalance - totalSentAmount + receivedAmount
            )
          )
      }

      withClue("should have two new pending transactions") {
        val tnxs =
          transactions.awaitItem()
            .apply {
              size.shouldBe(3)
            }

        // initial balance (confirmed)
        tnxs[0]
          .should {
            it.fee.shouldBeNull()
            it.subtotal.shouldBe(initialBalance)
            it.total.shouldBe(initialBalance)
            it.shouldBeIncoming()
            it.confirmationStatus
              .shouldBeTypeOf<Confirmed>()
              .blockTime.height.shouldBe(401)
          }

        // sent (pending)
        tnxs[1]
          .should {
            it.fee.shouldBe(fee)
            it.subtotal.shouldBe(sentAmount)
            it.total.shouldBe(totalSentAmount)
            it.shouldBeOutgoing()
            it.shouldBePending()
          }

        // received (pending)
        tnxs[2]
          .should {
            it.fee.shouldBeNull()
            it.subtotal.shouldBe(receivedAmount)
            it.total.shouldBe(receivedAmount)
            it.shouldBeIncoming()
            it.shouldBePending()
          }
      }

      withClue("mine block and sync") {
        wallet.mineBlock()
        wallet.sync()
      }

      withClue(
        "should have balance with subtracted confirmed amount and added received confirmed amount"
      ) {
        balance.awaitItem()
          .shouldBe(
            BitcoinBalance(
              immature = BitcoinMoney.zero(),
              trustedPending = BitcoinMoney.zero(),
              untrustedPending = BitcoinMoney.zero(),
              confirmed = initialBalance - totalSentAmount + receivedAmount,
              spendable = initialBalance - totalSentAmount + receivedAmount,
              total = initialBalance - totalSentAmount + receivedAmount
            )
          )
      }

      withClue("should have all transactions confirmed") {
        val tnxs =
          transactions.awaitItem()
            .apply {
              size.shouldBe(3)
            }

        // initial balance (confirmed)
        tnxs[0]
          .should {
            it.fee.shouldBeNull()
            it.subtotal.shouldBe(initialBalance)
            it.total.shouldBe(initialBalance)
            it.shouldBeIncoming()
            it.confirmationStatus
              .shouldBeTypeOf<Confirmed>()
              .blockTime.height.shouldBe(401)
          }

        // sent (confirmed)
        tnxs[1]
          .should {
            it.fee.shouldBe(fee)
            it.subtotal.shouldBe(sentAmount)
            it.total.shouldBe(totalSentAmount)
            it.shouldBeOutgoing()
            it.confirmationStatus
              .shouldBeTypeOf<Confirmed>()
              .blockTime.height.shouldBe(402)
          }

        // received (confirmed)
        tnxs[2]
          .should {
            it.fee.shouldBeNull()
            it.subtotal.shouldBe(receivedAmount)
            it.total.shouldBe(receivedAmount)
            it.shouldBeIncoming()
            it.confirmationStatus
              .shouldBeTypeOf<Confirmed>()
              .blockTime.height.shouldBe(402)
          }
      }

      balance.expectNoEvents()
      balance.cancelAndIgnoreRemainingEvents()
      transactions.expectNoEvents()
      transactions.cancelAndIgnoreRemainingEvents()
    }
  }
})
