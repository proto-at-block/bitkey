package build.wallet.bitcoin.transactions

import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimator
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Incoming
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.UtxoConsolidation
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.bitcoin.wallet.SpendingWallet.PsbtConstructionMethod
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensure
import build.wallet.ensureNotNull
import build.wallet.logging.logFailure
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.BTC
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.math.ceil

@BitkeyInject(AppScope::class)
class SpeedUpDepositServiceImpl(
  private val feeRateEstimator: BitcoinFeeRateEstimator,
  private val bitcoinWalletService: BitcoinWalletService,
  private val accountService: AccountService,
) : SpeedUpDepositService {
  override suspend fun prepareSpeedUpDepositTransaction(
    transaction: BitcoinTransaction,
  ): Result<SpeedUpDepositTransaction, Error> =
    coroutineBinding {
      // Validate eligibility: must be incoming and pending
      ensure(transaction.transactionType == Incoming) {
        Error("CPFP failed: transaction is not incoming")
      }
      ensure(transaction.confirmationStatus == Pending) {
        Error("CPFP failed: transaction is already confirmed")
      }

      val account = accountService.getAccount<FullAccount>().bind()

      // We need parent fee and weight to calculate the required child fee
      val parentFee = ensureNotNull(transaction.fee) {
        Error("CPFP failed: parent transaction missing fee")
      }

      val parentWeight = ensureNotNull(transaction.weight ?: transaction.vsize?.let { it * 4uL }) {
        Error("CPFP failed: parent transaction missing weight/vsize")
      }

      val wallet = ensureNotNull(bitcoinWalletService.spendingWallet().value) {
        Error("CPFP failed: no spending wallet available")
      }

      // Follow the CPFP chain to find the tip UTXO and accumulated fees/weights.
      // If no CPFP child exists yet, this returns the parent's UTXO directly.
      // If a CPFP child already exists, this returns the tip and the sum of all fees and weights.
      val chainInfo = ensureNotNull(findCpfpChainTip(transaction.id)) {
        Error("CPFP failed: no spendable UTXO found in CPFP chain for parent tx")
      }

      // Get a fresh wallet address for the self-transfer
      val recipientAddress = wallet.getNewAddress()
        .logFailure { "CPFP failed: could not get new address" }
        .mapError { Error("CPFP failed: could not get new address") }
        .bind()

      // Step 1: Build a dummy PSBT at minimum relay rate to determine the actual child tx weight.
      val dummyPsbt = wallet
        .createSignedPsbt(
          PsbtConstructionMethod.Cpfp(
            utxoOutpoint = chainInfo.utxo.outPoint,
            recipientAddress = recipientAddress,
            feePolicy = FeePolicy.MinRelayRate
          )
        )
        .logFailure { "CPFP: Unable to build dummy PSBT for weight estimation" }
        .mapError { throwable ->
          when (throwable) {
            is BdkError.InsufficientFunds -> throwable
            else -> Error("CPFP: Unable to build dummy PSBT for weight estimation")
          }
        }
        .bind()

      // Step 2: Calculate the required child fee.
      // The fee must bring the entire package (parent + intermediates + new child) to the target rate.
      val newChildWeight = dummyPsbt.vsize.toULong() * 4uL
      val targetFeeRate = feeRateEstimator.estimatedFeeRateForTransaction(
        networkType = account.config.bitcoinNetworkType,
        estimatedTransactionPriority = FASTEST
      )

      val parentFeeSats = BTC.fractionalUnitValueFromUnitValue(parentFee.value).longValue()
      val totalAncestorFeeSats = parentFeeSats + chainInfo.intermediateFeeSats
      val totalAncestorWeight = parentWeight + chainInfo.intermediateWeight

      val childFee = calculateRequiredChildFee(
        ancestorFeeSats = totalAncestorFeeSats,
        ancestorWeight = totalAncestorWeight,
        childWeight = newChildWeight,
        targetFeeRate = targetFeeRate
      )

      ensure(childFee > 0) {
        Error("CPFP failed: original fee is sufficient")
      }

      // Clamp to at least the dummy PSBT's min-relay fee so BDK never rejects with FeeTooLow.
      // The package-target math alone can produce a positive childFee that still falls below the
      // network relay minimum when the parent is already close to the target rate.
      val minRelayFeeSats = dummyPsbt.fee.amount.fractionalUnitValue.longValue()
      val finalChildFee = maxOf(childFee, minRelayFeeSats)

      // Step 3: Build the real PSBT with the calculated absolute fee.
      val psbt = wallet
        .createSignedPsbt(
          PsbtConstructionMethod.Cpfp(
            utxoOutpoint = chainInfo.utxo.outPoint,
            recipientAddress = recipientAddress,
            feePolicy = FeePolicy.Absolute(Fee(amount = BitcoinMoney.sats(BigInteger(finalChildFee))))
          )
        )
        .logFailure { "CPFP: Unable to build final CPFP PSBT" }
        .mapError { throwable ->
          when (throwable) {
            is BdkError.InsufficientFunds -> throwable
            else -> Error("CPFP: Unable to build final CPFP PSBT")
          }
        }
        .bind()

      // Step 4: Calculate actual child fee rate from the built PSBT
      val actualChildFeeRate = if (psbt.vsize > 0) {
        FeeRate(satsPerVByte = psbt.fee.amount.fractionalUnitValue.floatValue(false) / psbt.vsize)
      } else {
        targetFeeRate
      }

      // Step 5: Compute the child self-transfer amount as tip UTXO value minus child fee.
      // This reflects what will actually land in the wallet after the child confirms,
      // and stays correct when re-bumping (tip value tracks the chain, not the original deposit).
      val tipUtxoValue = BitcoinMoney.sats(BigInteger(chainInfo.utxo.txOut.value.toLong()))
      val transferAmount = tipUtxoValue - psbt.fee.amount

      SpeedUpDepositTransaction(
        psbt = psbt,
        childFeeRate = actualChildFeeRate,
        parentTxid = transaction.id,
        parentFee = Fee(amount = parentFee),
        childFee = psbt.fee,
        transferAmount = transferAmount
      )
    }

  /**
   * Follows the CPFP chain from [txId] to find the tip UTXO, accumulating fees and
   * weights of any intermediate CPFP children along the way.
   *
   * @param txId The txid to inspect at this step (starts as the original parent txid).
   * @param depth Current recursion depth; stops at 10 to guard against unexpected cycles.
   * @param accFeeSats Accumulated fees of intermediate CPFP children so far (sats).
   * @param accWeight Accumulated weight of intermediate CPFP children so far (WU).
   * @return Chain info with the tip UTXO and accumulated ancestor data, or null if no
   *         spendable UTXO is found within the depth limit.
   */
  private fun findCpfpChainTip(
    txId: String,
    depth: Int = 0,
    accFeeSats: Long = 0L,
    accWeight: ULong = 0uL,
  ): CpfpChainInfo? {
    val transactionsData = bitcoinWalletService.transactionsData().value ?: return null
    val unconfirmedUtxos = transactionsData.utxos.unconfirmed
    val directCandidate = unconfirmedUtxos
      .filter { it.outPoint.txid == txId }
      .maxByOrNull { it.txOut.value }
      ?.let { utxo ->
        CpfpChainInfo(
          utxo = utxo,
          depth = depth,
          intermediateFeeSats = accFeeSats,
          intermediateWeight = accWeight
        )
      }

    // Do not return immediately on a direct UTXO match: the parent can still have a smaller
    // leftover output while a larger spendable CPFP tip exists deeper in a descendant branch.
    // We evaluate both direct and descendant candidates, then select the best one.

    // Enforce a max depth of 10 to prevent potential infinite traversal
    if (depth >= 10) return directCandidate

    // Find unconfirmed CPFP children spending from this tx. Try each branch in case some
    // have spendable tips and others don't (e.g., parallel CPFP attempts or low-value outputs).
    val descendantCandidates = transactionsData.transactions
      .filter { tx ->
        tx.transactionType == UtxoConsolidation &&
          tx.confirmationStatus == Pending &&
          tx.inputs.any { it.outpoint.txid == txId }
      }
      // Recurse into every child branch and keep all reachable tips. We cannot short-circuit
      // on the first match because a later branch can have a larger spendable tip UTXO.
      .mapNotNull { childTx ->
        val childFeeSats = childTx.fee?.let {
          BTC.fractionalUnitValueFromUnitValue(it.value).longValue()
        } ?: 0L
        // Weight is required to compute the package fee; skip this branch if unavailable.
        val childWeight = childTx.weight
          ?: childTx.vsize?.let { it * 4uL }
          ?: return@mapNotNull null

        findCpfpChainTip(
          txId = childTx.id,
          depth = depth + 1,
          accFeeSats = accFeeSats + childFeeSats,
          accWeight = accWeight + childWeight
        )
      }
    // Build the full candidate set from:
    // 1) directCandidate: a still-unspent output on the current txId, and
    // 2) descendantCandidates: reachable tips from each CPFP child branch.
    // We must evaluate all of them to avoid missing a better branch when multiple
    // CPFP paths exist in parallel.
    val allCandidates = buildList {
      directCandidate?.let(::add)
      addAll(descendantCandidates)
    }
    // Return the best spendable tip by largest value (better chance to fund the bump).
    // If values tie, prefer shallower depth to minimize accumulated ancestors.
    return allCandidates.maxWithOrNull(
      compareBy<CpfpChainInfo> { it.utxo.txOut.value }
        .thenBy { it.depth }
    )
  }

  /**
   * Calculates the fee the new child transaction must pay so that the entire package
   * (parent + intermediates + new child) achieves the target fee rate.
   *
   * Formula:
   *   child_fee = target_rate * (ancestor_weight + child_weight) / 4 - ancestor_fees
   *
   * @param ancestorFeeSats Total fees of parent + all intermediate CPFP children (in sats).
   * @param ancestorWeight Total weight of parent + all intermediate CPFP children (in WU).
   * @param childWeight Weight of the new child transaction (in WU).
   * @param targetFeeRate Desired fee rate for the package (in sat/vB).
   * @return Required child fee in satoshis. Negative means ancestors already pay enough.
   */
  private fun calculateRequiredChildFee(
    ancestorFeeSats: Long,
    ancestorWeight: ULong,
    childWeight: ULong,
    targetFeeRate: FeeRate,
  ): Long {
    val totalWeight = ancestorWeight + childWeight
    // Convert weight units to vbytes (ceiling) so partial vbytes round up conservatively
    val totalVbytes = (totalWeight + 3uL) / 4uL
    // Use ceil() to ensure the package meets or exceeds the target fee rate;
    // floor-truncation via toLong() can leave the package 1+ sat short of the target.
    val requiredTotalFee = ceil(targetFeeRate.satsPerVByte * totalVbytes.toLong().toFloat()).toLong()
    return requiredTotalFee - ancestorFeeSats
  }
}

/**
 * Information about a CPFP chain from the original parent to the current tip.
 *
 * @property utxo The spendable UTXO at the tip of the chain.
 * @property depth Number of intermediate CPFP children (0 = direct parent UTXO).
 * @property intermediateFeeSats Total fees paid by all intermediate CPFP children.
 * @property intermediateWeight Total weight of all intermediate CPFP children.
 */
private data class CpfpChainInfo(
  val utxo: BdkUtxo,
  val depth: Int,
  val intermediateFeeSats: Long,
  val intermediateWeight: ULong,
) {
  init {
    require(depth >= 0) { "depth must be non-negative, was $depth" }
    require(intermediateFeeSats >= 0L) {
      "intermediateFeeSats must be non-negative, was $intermediateFeeSats"
    }
  }
}
