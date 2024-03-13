package build.wallet.statemachine.send

import build.wallet.ui.model.Model
import dev.zacsweers.redacted.annotations.Redacted

@Redacted
data class TransactionDetailsModel(
  val transactionDetailModelType: TransactionDetailModelType,
  val transactionSpeedText: String,
) : Model()

/**
 * Transaction detail display type for transaction confirmation and initiated screens.
 *
 * @property transferAmountText the "Recipient receives" amount.
 * @property totalAmountPrimaryText the net total amount (with fees) that the customer will be spending, in fiat.
 * @property totalAmountSecondaryText the net total amount (with fees) that the customer will be spending, in sats.
 */
sealed interface TransactionDetailModelType {
  val transferAmountText: String
  val totalAmountPrimaryText: String
  val totalAmountSecondaryText: String?

  /**
   * For a "regular" bitcoin send/receive transaction.
   *
   * @property feeAmountText The fees paid for this transaction.
   */
  data class Regular(
    override val transferAmountText: String,
    override val totalAmountPrimaryText: String,
    override val totalAmountSecondaryText: String?,
    val feeAmountText: String,
  ) : TransactionDetailModelType

  /**
   * For a bitcoin send transaction to be sped up via replace-by-fee (RBF).
   *
   * @property oldFeeAmountText The initial fees paid for this transaction.
   * @property feeDifferenceText The cost difference to speed up the transaction.
   */
  data class SpeedUp(
    override val transferAmountText: String,
    override val totalAmountPrimaryText: String,
    override val totalAmountSecondaryText: String?,
    val oldFeeAmountText: String,
    val feeDifferenceText: String,
  ) : TransactionDetailModelType
}
