package build.wallet.bitcoin.utxo

import build.wallet.bdk.bindings.BdkUtxoMock
import build.wallet.bdk.bindings.BdkUtxoMock2
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UtxosTests : FunSpec({
  test("can have confirmed and unconfirmed UTXOs") {
    val utxos = Utxos(
      confirmed = setOf(BdkUtxoMock),
      unconfirmed = setOf(BdkUtxoMock2)
    )

    utxos.confirmed shouldBe setOf(BdkUtxoMock)
    utxos.unconfirmed shouldBe setOf(BdkUtxoMock2)
  }

  test("cannot have UTXOs that are both confirmed and unconfirmed") {
    shouldThrow<IllegalArgumentException> {
      Utxos(
        confirmed = setOf(BdkUtxoMock),
        unconfirmed = setOf(BdkUtxoMock, BdkUtxoMock2)
      )
    }.message.shouldBe("UTXOs cannot be both confirmed and unconfirmed.")
  }

  test("can have UTXOs that are only confirmed") {
    Utxos(
      confirmed = setOf(BdkUtxoMock),
      unconfirmed = emptySet()
    )
  }

  test("can have UTXOs that are only unconfirmed") {
    Utxos(
      confirmed = emptySet(),
      unconfirmed = setOf(BdkUtxoMock)
    )
  }

  test("can have no UTXOs") {
    Utxos(
      confirmed = emptySet(),
      unconfirmed = emptySet()
    )
  }
})
