package build.wallet.bitcoin.utxo

import build.wallet.bdk.bindings.BdkOutPoint
import build.wallet.bdk.bindings.BdkTxIn
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.bdk.bitcoinAmount
import build.wallet.bitcoin.wallet.CoinSelectionStrategy
import build.wallet.money.BitcoinMoney
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

/**
 * User-chosen confirmed UTXOs for a Regular send.
 *
 * Invariants:
 * - [outpoints] is non-empty
 * - every outpoint was present in the confirmed inventory at creation
 * - [spendableTotal] is derived from those UTXOs' values
 */
class CoinControl private constructor(
  private val utxos: Set<BdkUtxo>,
) {
  val outpoints: Set<BdkOutPoint>
    get() = utxos.map { it.outPoint }.toSet()

  val spendableTotal: BitcoinMoney
    get() = utxos.fold(BitcoinMoney.zero()) { acc, utxo -> acc + utxo.bitcoinAmount }

  val count: Int get() = utxos.size

  fun toStrictStrategy(): CoinSelectionStrategy.Strict =
    CoinSelectionStrategy.Strict(
      inputs = utxos.map { it.toSelectionInput() }.toSet()
    )

  companion object {
    fun create(
      inventory: Set<BdkUtxo>,
      selected: Set<BdkOutPoint>,
    ): Result<CoinControl, CoinControlError> {
      if (selected.isEmpty()) {
        return Err(CoinControlError.EmptySelection)
      }

      val inventoryByOutpoint = inventory.associateBy { it.outPoint }
      val missing = selected.filterNot { it in inventoryByOutpoint }.toSet()
      if (missing.isNotEmpty()) {
        return Err(CoinControlError.UnknownOrUnconfirmedOutpoints(missing))
      }

      val chosen = selected.map { inventoryByOutpoint.getValue(it) }.toSet()
      return Ok(CoinControl(chosen))
    }
  }
}

sealed class CoinControlError {
  data object EmptySelection : CoinControlError()

  data class UnknownOrUnconfirmedOutpoints(
    val bad: Set<BdkOutPoint>,
  ) : CoinControlError()
}

fun CoinControl?.toCoinSelectionStrategy(): CoinSelectionStrategy =
  this?.toStrictStrategy() ?: CoinSelectionStrategy.Default

private fun BdkUtxo.toSelectionInput(): BdkTxIn =
  BdkTxIn(
    outpoint = outPoint,
    sequence = 0u,
    witness = emptyList()
  )
