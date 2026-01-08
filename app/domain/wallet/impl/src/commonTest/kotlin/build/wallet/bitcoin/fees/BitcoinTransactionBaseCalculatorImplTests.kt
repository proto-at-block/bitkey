package build.wallet.bitcoin.fees

import build.wallet.bitcoin.balance.BitcoinBalanceFake
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.ExactAmount
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.SendAll
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.*
import build.wallet.money.BitcoinMoney
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.persistentMapOf

class BitcoinTransactionBaseCalculatorImplTests : FunSpec({
  val calculator = BitcoinTransactionBaseCalculatorImpl()
  val feeMap =
    persistentMapOf(
      FASTEST to Fee(BitcoinMoney.sats(1000)),
      THIRTY_MINUTES to Fee(BitcoinMoney.sats(300)),
      SIXTY_MINUTES to Fee(BitcoinMoney.sats(150))
    )

  context("Exact Amount") {
    test("minimum is send amount + fees[SIXTY_MINUTES]") {
      val amountToSend = BitcoinMoney.sats(50_000)
      calculator.minimumSatsRequiredForTransaction(
        walletBalance = BitcoinBalanceFake,
        sendAmount = ExactAmount(amountToSend),
        fees = feeMap
      ).shouldBe(
        amountToSend + feeMap[SIXTY_MINUTES]!!.amount
      )
    }
  }

  context("Send All") {
    test("minimum is balance - fees[FASTEST]") {
      calculator.minimumSatsRequiredForTransaction(
        walletBalance = BitcoinBalanceFake,
        sendAmount = SendAll,
        fees = feeMap
      ).shouldBe(
        BitcoinBalanceFake.total - feeMap[FASTEST]!!.amount
      )
    }
  }
})
