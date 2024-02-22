package build.wallet.bitcoin.wallet

import build.wallet.bdk.bindings.BdkBumpFeeTxBuilderFactory
import build.wallet.bdk.bindings.BdkTxBuilderFactory
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

  /*
   * Enum class representing the different ways to construct PSBTs with BDK.
   */
  sealed interface PsbtConstructionMethod {
    /**
     * Uses [BdkTxBuilderFactory.txBuilder] to construct a new, fresh bitcoin transaction.
     */
    data class Regular(
      val recipientAddress: BitcoinAddress,
      val amount: BitcoinTransactionSendAmount,
      val feePolicy: FeePolicy,
    ) : PsbtConstructionMethod

    /**
     * Uses [BdkBumpFeeTxBuilderFactory.bumpFeeTxBuilder] to construct a new, fresh bitcoin
     * transaction.
     */
    data class BumpFee(
      val txid: String,
      val feeRate: FeeRate,
    ) : PsbtConstructionMethod
  }
}
