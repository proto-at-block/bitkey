package build.wallet.bitcoin.utxo

import build.wallet.bdk.bindings.BdkOutPoint
import build.wallet.bdk.bindings.BdkUtxoMock
import build.wallet.bdk.bindings.BdkUtxoMock2
import build.wallet.bitcoin.wallet.CoinSelectionStrategy
import build.wallet.money.BitcoinMoney
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class CoinControlTests : FunSpec({
  val utxoA = BdkUtxoMock
  val utxoB = BdkUtxoMock2.copy(
    outPoint = BdkOutPoint(txid = "def", vout = 1u)
  )
  val inventory = setOf(utxoA, utxoB)

  test("create accepts a non-empty confirmed subset") {
    val coinControl = CoinControl.create(
      inventory = inventory,
      selected = setOf(utxoA.outPoint)
    ).get().shouldNotBeNull()

    coinControl.count shouldBe 1
    coinControl.outpoints shouldBe setOf(utxoA.outPoint)
    coinControl.spendableTotal shouldBe BitcoinMoney.sats(1)
  }

  test("create sums spendableTotal across selected UTXOs") {
    val coinControl = CoinControl.create(
      inventory = inventory,
      selected = setOf(utxoA.outPoint, utxoB.outPoint)
    ).get().shouldNotBeNull()

    coinControl.spendableTotal shouldBe BitcoinMoney.sats(3)
  }

  test("create rejects empty selection") {
    CoinControl.create(inventory = inventory, selected = emptySet()).getError() shouldBe
      CoinControlError.EmptySelection
  }

  test("create rejects unknown or unconfirmed outpoints") {
    val unknown = BdkOutPoint(txid = "missing", vout = 0u)
    CoinControl.create(
      inventory = setOf(utxoA),
      selected = setOf(utxoA.outPoint, unknown)
    ).getError() shouldBe CoinControlError.UnknownOrUnconfirmedOutpoints(setOf(unknown))
  }

  test("toStrictStrategy maps selected outpoints") {
    val coinControl = CoinControl.create(
      inventory = inventory,
      selected = setOf(utxoB.outPoint)
    ).get().shouldNotBeNull()

    val strategy = coinControl.toStrictStrategy()
    strategy.inputs.map { it.outpoint }.toSet() shouldBe setOf(utxoB.outPoint)
  }

  test("null CoinControl maps to Default strategy") {
    (null as CoinControl?).toCoinSelectionStrategy() shouldBe CoinSelectionStrategy.Default
  }

  test("present CoinControl maps to Strict strategy") {
    val coinControl = CoinControl.create(
      inventory = inventory,
      selected = setOf(utxoA.outPoint)
    ).get().shouldNotBeNull()

    coinControl.toCoinSelectionStrategy().shouldBeInstanceOf<CoinSelectionStrategy.Strict>()
  }
})
