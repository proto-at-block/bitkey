package build.wallet.bitcoin.wallet

import build.wallet.bdk.bindings.BdkOutPoint
import build.wallet.bdk.bindings.BdkScript
import build.wallet.bdk.bindings.BdkTxIn
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.Psbt
import com.github.michaelbull.result.Result

/**
 * Represents a spending bitcoin wallet. Provides same APIs as [WatchingWallet] but with additional
 * signing methods which use descriptor's private key.
 *
 * Instances of this interfaces can be created using [SpendingWalletProvider].
 */
interface SpendingWallet : WatchingWallet {
  /**
   * Signs a [Psbt] with wallet's private keys.
   */
  suspend fun signPsbt(psbt: Psbt): Result<Psbt, Throwable>

  /**
   * Same as [WatchingWallet.createPsbt] but also signs the created [Psbt] with private keys.
   */
  suspend fun createSignedPsbt(constructionType: PsbtConstructionMethod): Result<Psbt, Throwable>

  /**
   * Checks if the balance is spendable.
   */
  suspend fun isBalanceSpendable(): Result<Boolean, Error>

  /**
   * Enum class representing the different ways to construct PSBTs with BDK.
   */
  sealed interface PsbtConstructionMethod {
    /**
     * Represents a regular transaction performed by the customer - sending
     * a certain amount or max (drain all) to a recipient address.
     *
     * @param recipientAddress - Address to which the funds will be sent.
     * @param amount - Amount (exact or all) to be sent to the recipient address.
     */
    data class Regular(
      val recipientAddress: BitcoinAddress,
      val amount: BitcoinTransactionSendAmount,
      val feePolicy: FeePolicy,
      val coinSelectionStrategy: CoinSelectionStrategy = CoinSelectionStrategy.Default,
    ) : PsbtConstructionMethod

    /**
     * Represents a transaction that spends all funds from a set of UTXOs. UTXOs have to
     * belong to the active spending wallet.
     *
     * @param recipientAddress - Address to which the funds will be sent.
     * @param feePolicy - Fee policy to be used in the transaction.
     * @param utxos - UTXOs that will be drained from the wallet and spent in the transaction.
     */
    data class DrainAllFromUtxos(
      val recipientAddress: BitcoinAddress,
      val feePolicy: FeePolicy,
      val utxos: Set<BdkUtxo>,
    ) : PsbtConstructionMethod {
      init {
        require(utxos.isNotEmpty()) { "UTXOs can't be empty." }
      }
    }

    /**
     * Represents a transaction that bumps the fee of an existing transaction.
     *
     * @param txid - The transaction ID of the transaction to bump the fee of.
     * @param feeRate - The new fee rate to use.
     */
    data class FeeBump(
      val txid: String,
      val feeRate: FeeRate,
    ) : PsbtConstructionMethod

    /**
     * Fee bump for transactions requiring output shrinking.
     *
     * Used for sweeps and single-UTXO consolidations where BDK's standard BumpFeeTxBuilder
     * cannot add inputs to cover the fee increase. Instead, the output amount is reduced.
     *
     * Uses BumpFeeTxBuilder.drainTo() which clears recipients and drains all funds
     * to the specified script.
     *
     * @param txid - The transaction ID of the transaction to bump the fee of.
     * @param feeRate - The new fee rate to use.
     * @param drainToScript - The script to drain all funds to (typically the original output script).
     */
    data class FeeBumpWithDrain(
      val txid: String,
      val feeRate: FeeRate,
      val drainToScript: BdkScript,
    ) : PsbtConstructionMethod

    /**
     * Represents a Child-Pays-For-Parent (CPFP) transaction.
     *
     * Spends a single UTXO (from the parent or a prior CPFP child) and drains
     * all funds to the recipient address with the given fee policy. The fee is
     * set high enough so the combined parent+child package reaches the target rate.
     *
     * @param utxoOutpoint - The outpoint of the UTXO to spend (tip of the CPFP chain).
     * @param recipientAddress - Self-transfer address to drain funds to.
     * @param feePolicy - Fee policy (absolute fee for real tx, MinRelayRate for weight estimation).
     */
    data class Cpfp(
      val utxoOutpoint: BdkOutPoint,
      val recipientAddress: BitcoinAddress,
      val feePolicy: FeePolicy,
    ) : PsbtConstructionMethod
  }
}

/**
 * Specifies the coin selection strategy to be used when building a [Psbt].
 */
sealed interface CoinSelectionStrategy {
  /**
   * Default coin selection strategy - lets the BDK choose from all available inputs
   */
  data object Default : CoinSelectionStrategy

  /**
   * Strict coin selection strategy - only use the provided [inputs]
   */
  data class Strict(val inputs: Set<BdkTxIn>) : CoinSelectionStrategy

  /**
   * Preselected coin selection strategy - use the provided [inputs] and let the BDK pull in more if needed
   */
  data class Preselected(val inputs: Set<BdkTxIn>) : CoinSelectionStrategy
}
