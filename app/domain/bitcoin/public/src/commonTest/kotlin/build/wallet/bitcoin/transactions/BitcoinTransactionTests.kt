package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.BdkTxIn
import build.wallet.bdk.bindings.BdkTxOut
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Outgoing
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.BTC
import build.wallet.time.someInstant
import com.ionspin.kotlin.bignum.integer.BigInteger
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.ImmutableList

class BitcoinTransactionTests : FunSpec({

  test("Init doesn't throw exception for null fee") {
    shouldNotThrow<IllegalArgumentException> {
      makeTransaction(
        fee = null
      )
    }
  }

  test("Truncated ID") {
    makeTransaction().truncatedId().shouldBe("4a5e...a33b")
  }

  test("Truncated recipient address") {
    makeTransaction().truncatedRecipientAddress().shouldBe("tb1q...th2n")
  }

  test("Full formatted recipient address") {
    makeTransaction().chunkedRecipientAddress()
      .shouldBe("tb1q mg9j dpms nj5s eanj p20z yyxt 0de4 wgf9 q7cn kzdj c0sc m6sp vuyq 2qth 2n")
  }

  test("Returns null fee rate if fee does not exist") {
    makeTransaction().feeRate().shouldBeNull()
  }

  test("Returns correct fee rate if fee exists") {
    makeTransaction(fee = BitcoinMoney(BTC, BTC.unitValueFromFractionalUnitValue(BigInteger(3250))))
      .feeRate().shouldBe(FeeRate(satsPerVByte = 10F))
  }
})

private fun makeTransaction(
  id: String = "4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b",
  subtotal: BitcoinMoney = BitcoinMoney.sats(1_000),
  total: BitcoinMoney = BitcoinMoney.sats(1_000),
  fee: BitcoinMoney? = null,
  transactionType: TransactionType = Outgoing,
  inputs: ImmutableList<BdkTxIn> = emptyImmutableList(),
  outputs: ImmutableList<BdkTxOut> = emptyImmutableList(),
): BitcoinTransaction =
  BitcoinTransaction(
    id = id,
    broadcastTime = someInstant,
    estimatedConfirmationTime = null,
    confirmationStatus = Pending,
    recipientAddress =
      BitcoinAddress(
        address = "tb1qmg9jdpmsnj5seanjp20zyyxt0de4wgf9q7cnkzdjc0scm6spvuyq2qth2n"
      ),
    subtotal = subtotal,
    total = total,
    fee = fee,
    weight = 1300UL,
    vsize = 325UL,
    transactionType = transactionType,
    inputs = inputs,
    outputs = outputs
  )
