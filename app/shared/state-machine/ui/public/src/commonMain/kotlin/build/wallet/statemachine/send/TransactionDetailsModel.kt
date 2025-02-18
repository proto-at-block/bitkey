package build.wallet.statemachine.send

import build.wallet.ui.model.Model
import dev.zacsweers.redacted.annotations.Redacted

@Redacted
data class TransactionDetailsModel(
  val transactionDetailModelType: TransactionDetailModelType,
  val transactionSpeedText: String,
) : Model

/**
 * Transaction detail display type for transaction confirmation and initiated screens.
 *
 * Amounts have both a main and secondary text. When we're able to convert btc to fiat, the primary
 * text will be fiat and the secondary will be btc/sats. If we aren't, and fiat is "null", the primary
 * text will be btc/sats and the secondary text will be null.
 *
 * @property transferAmountText the amount.
 * @property totalAmountPrimaryText the net total amount (with fees) that the customer will be spending, in fiat.
 * @property totalAmountSecondaryText the net total amount (with fees) that the customer will be spending, in sats.
 */
sealed interface TransactionDetailModelType {
  val transferAmountText: String
  val transferAmountSecondaryText: String?
  val totalAmountPrimaryText: String
  val totalAmountSecondaryText: String?

  /**
   * For a "regular" bitcoin send/receive transaction.
   *
   * @property feeAmountText The fees paid for this transaction.
   */
  data class Regular(
    override val transferAmountText: String,
    override val transferAmountSecondaryText: String?,
    override val totalAmountPrimaryText: String,
    override val totalAmountSecondaryText: String?,
    val feeAmountText: String,
    val feeAmountSecondaryText: String?,
  ) : TransactionDetailModelType

  /**
   * For a bitcoin send transaction to be sped up via replace-by-fee (RBF).
   *
   * @property oldFeeAmountText The initial fees paid for this transaction.
   * @property feeDifferenceText The cost difference to speed up the transaction.
   * @property totalFeeText The new fee for the transaction
   */
  data class SpeedUp(
    override val transferAmountText: String,
    override val transferAmountSecondaryText: String?,
    override val totalAmountPrimaryText: String,
    override val totalAmountSecondaryText: String?,
    val oldFeeAmountText: String,
    val oldFeeAmountSecondaryText: String?,
    val feeDifferenceText: String,
    val feeDifferenceSecondaryText: String?,
    val totalFeeText: String,
    val totalFeeSecondaryText: String?,
  ) : TransactionDetailModelType
}
